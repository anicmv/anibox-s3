package com.github.anicmv.service;

import com.github.anicmv.config.S3ClientManager;
import com.github.anicmv.config.S3StorageProperties;
import com.github.anicmv.dto.delete.DeleteResponse;
import com.github.anicmv.dto.delete.DeleteResult;
import com.github.anicmv.dto.file.*;
import com.github.anicmv.dto.rename.RenameResult;
import com.github.anicmv.dto.response.R;
import com.github.anicmv.dto.result.ServiceDeleteResult;
import com.github.anicmv.dto.result.ServiceFileInfoResult;
import com.github.anicmv.dto.result.ServiceRenameResult;
import com.github.anicmv.dto.result.ServiceUploadResult;
import com.github.anicmv.dto.upload.UploadResponse;
import com.github.anicmv.dto.upload.UploadResult;
import com.github.anicmv.exception.FileValidationException;
import com.github.anicmv.exception.StorageConfigurationException;
import com.github.anicmv.exception.StorageServiceException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * @author anicmv
 * @date 2025/8/9 15:56
 * @description 图片上传服务类，提供图片的上传、删除、查询、列表以及修改等操作。
 */
@Slf4j
@Service
public class ImageUploadService {

    @Resource
    private S3ClientManager clientManager;

    @Resource
    private S3StorageProperties storageProperties;

    @Resource
    private FileValidationService fileValidationService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // 缓存已创建的桶，避免重复检查
    private final Map<String, Set<String>> createdBuckets = new ConcurrentHashMap<>();

    public ResponseEntity<R<UploadResponse>> uploadImage(MultipartFile file, HttpServletRequest request) {
        // 打印ip
        String clientIp = fileValidationService.getClientIp(request);
        log.info("开始处理文件上传请求 - 文件名: {}, 大小: {} bytes, 客户端IP: {}",
                file.getOriginalFilename(), file.getSize(), clientIp);
        // 文件验证
        fileValidationService.validateFile(file);
        String fileName = generateFileName(file.getOriginalFilename());
        // 上传
        UploadResult uploadResult = upload(file, fileName);
        // 响应
        return uploadResponse(file.getOriginalFilename(), uploadResult);
    }


    private UploadResult upload(MultipartFile file, String fileName) {

        Map<String, S3Client> targetClients = getTargetClients();

        if (targetClients.isEmpty()) {
            throw new StorageConfigurationException("没有可用的存储服务");
        }


        long startTime = System.currentTimeMillis();
        List<CompletableFuture<ServiceUploadResult>> futures = new ArrayList<>();

        for (Map.Entry<String, S3Client> entry : targetClients.entrySet()) {
            String serviceName = entry.getKey();
            S3Client client = entry.getValue();

            CompletableFuture<ServiceUploadResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return uploadToService(serviceName, client, file, fileName);
                } catch (Exception e) {
                    log.error("Failed to upload to service: {}", serviceName, e);
                    return new ServiceUploadResult(serviceName, false, null, e.getMessage());
                }
            }, executorService);

            futures.add(future);
        }

        // 等待所有上传完成
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        List<ServiceUploadResult> results = allOf.thenApply(v ->
                futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
        ).join();

        long uploadTime = System.currentTimeMillis() - startTime;
        return new UploadResult(fileName, results, uploadTime);
    }


    private ResponseEntity<R<UploadResponse>> uploadResponse(String fileName, UploadResult result) {
        // 检查上传结果
        if (!result.allSuccessfulUpload()) {
            String errorMessage = "有存储服务上传失败";
            log.error("文件上传失败 - 文件名: {}, 原因: {}", fileName, errorMessage);
            throw new StorageConfigurationException(errorMessage);
        }

        // 转换为响应DTO
        UploadResponse response = convertToUploadResponse(result);

        log.info("文件上传成功 - 文件名: {}, 耗时: {}ms, 成功服务数: {}/{}",
                result.fileName(), result.uploadTimeMs(),
                result.getSuccessCount(), result.getTotalServices());

        return ResponseEntity.ok(R.success("文件上传成功", response));
    }


    private String generateFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s_%s%s", timestamp, uuid, extension);
    }

    private Map<String, S3Client> getTargetClients() {
        Map<String, S3Client> allClients = clientManager.getAllEnabledClients();

        switch (storageProperties.getUploadStrategy()) {
            case FIRST:
                return allClients.entrySet().stream()
                        .findFirst()
                        .map(entry -> Map.of(entry.getKey(), entry.getValue()))
                        .orElse(Collections.emptyMap());

            case ALL:
                return allClients;

            case SPECIFIC:
                Map<String, S3Client> specificClients = new HashMap<>();
                for (String target : storageProperties.getSpecificTargets()) {
                    S3Client client = allClients.get(target);
                    if (client != null) {
                        specificClients.put(target, client);
                    }
                }
                return specificClients;

            default:
                return Collections.emptyMap();
        }
    }




    /**
     * 修改生成访问URL的方法，使用动态桶名
     */
    private String generateAccessUrl(String serviceName, S3StorageProperties.S3ServiceConfig config,
                                     String key, S3Client client) {
        String bucketName = config.getBucket();
        // 如果配置了公开URL模式，使用模式生成URL
        if (config.getPublicUrlPattern() != null && !config.getPublicUrlPattern().isEmpty()) {
            return config.getPublicUrlPattern()
                    .replace("${endpoint}", config.getEndpoint())
                    .replace("${bucket}", bucketName)
                    .replace("${key}", key);
        }

        // 如果配置使用预签名URL，生成预签名URL
        if (config.isUsePresignedUrl()) {
            return generatePresignedUrl(config, key, serviceName, bucketName);
        }

        // 默认使用endpoint拼接
        return String.format("%s/%s/%s", config.getEndpoint(), bucketName, key);
    }


    /**
     * 修改预签名URL生成方法
     */
    private String generatePresignedUrl(S3StorageProperties.S3ServiceConfig config,
                                        String key, String serviceName, String bucketName) {
        try {
            S3Presigner preSigner = clientManager.getPreSigner(serviceName);
            if (preSigner == null) {
                log.warn("No preSigner available for service: {}, falling back to default URL", serviceName);
                return String.format("%s/%s/%s", config.getEndpoint(), bucketName, key);
            }
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(config.getPresignedUrlExpiry()))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = preSigner.presignGetObject(presignRequest);

            return presignedRequest.url().toString();

        } catch (Exception e) {
            log.error("Failed to generate presigned URL for service: {}", serviceName, e);
            return String.format("%s/%s/%s", config.getEndpoint(), bucketName, key);
        }
    }

    /**
     * 获取当前日期桶的文件列表（修改listFiles方法）
     */
    public ResponseEntity<R<FileListResponse>> listFiles(String prefix, int maxKeys, String continuationToken) {
        Map<String, S3Client> targetClients = clientManager.getAllEnabledClients();

        if (targetClients.isEmpty()) {
            throw new StorageConfigurationException("没有可用的存储服务");
        }

        Map.Entry<String, S3Client> firstService = targetClients.entrySet().iterator().next();
        String serviceName = firstService.getKey();
        S3Client client = firstService.getValue();
        FileListResult fileListResult = listFilesFromService(serviceName, client, prefix, maxKeys, continuationToken);
        FileListResponse response = convertToFileListResponse(fileListResult);
        return ResponseEntity.ok(R.success("文件列表查询成功", response));
    }


    /**
     * 修改listFilesFromService方法，支持动态桶名
     */
    private FileListResult listFilesFromService(String serviceName, S3Client client,
                                                String prefix, int maxKeys, String continuationToken) {
        S3StorageProperties.S3ServiceConfig config = storageProperties.getServices().get(serviceName);
        String bucketName = config.getBucket();
        String fullPrefix = prefix != null ? prefix : "";

        try {
            // 先确保桶存在
            ensureBucketExists(serviceName, client, bucketName);

            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(fullPrefix)
                    .maxKeys(maxKeys);

            if (continuationToken != null && !continuationToken.isEmpty()) {
                requestBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = client.listObjectsV2(requestBuilder.build());

            List<FileListResponse.FileItem> files = response.contents().stream()
                    .map(obj -> {
                        String fileName = obj.key().substring(fullPrefix.length());
                        String url = generateAccessUrl(serviceName, config, obj.key(), client);

                        return new FileListResponse.FileItem(fileName, obj.size(),
                                obj.lastModified(), List.of(url));
                    })
                    .collect(Collectors.toList());

            return new FileListResult(files, files.size(),
                    response.isTruncated(), response.nextContinuationToken());

        } catch (Exception e) {
            log.error("List files from {}/{} failed", serviceName, bucketName, e);
            throw new StorageServiceException("文件列表获取失败", e);
        }
    }


    /**
     * 获取桶创建统计信息
     */
    public Map<String, Object> getBucketStatistics(String bucketName) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("currentDateBucket", bucketName);
        stats.put("createdBucketsCount", createdBuckets.values().stream()
                .mapToInt(Set::size).sum());
        stats.put("createdBucketsByService", createdBuckets.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new ArrayList<>(entry.getValue())
                )));
        return stats;
    }

    /**
     * 设置桶的公开读权限策略（仅在支持的服务上执行）
     */
    private void setBucketPublicReadPolicy(S3Client client, String bucketName) {
        try {

            String policy = String.format("""
                    {
                        "Version":"2012-10-17",
                        "Statement":[
                            {
                            "Sid":"PublicRead",
                                "Effect":"Allow",
                                "Principal":{
                                    "AWS":[
                                        "*"
                                    ]
                                },
                                "Action":[
                                    "s3:GetObject"
                                ],
                                "Resource":[
                                    "arn:aws:s3:::%s/*"
                                ]
                            }
                        ]
                    }
                    """, bucketName);

            PutBucketPolicyRequest policyRequest = PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(policy)
                    .build();

            client.putBucketPolicy(policyRequest);
            log.info("桶公开读权限设置成功: {}", bucketName);

        } catch (S3Exception e) {
            if (e.statusCode() == 501) {
                log.info("服务不支持桶策略功能: {} - {}", bucketName, e.getMessage());
            } else {
                log.warn("设置桶公开读权限失败: {} - {}", bucketName, e.getMessage());
            }
        } catch (Exception e) {
            log.warn("设置桶公开读权限失败: {} - {}", bucketName, e.getMessage());
        }
    }

    /**
     * 创建桶并设置权限策略
     */
    private void createBucketWithPolicy(String serviceName, S3Client client, String bucketName) {
        try {
            // 1. 创建桶
            CreateBucketResponse createResponse = client.createBucket(request -> request.bucket(bucketName));
            log.info("桶创建成功: {} - {} - Location: {}", serviceName, bucketName, createResponse.location());

            // 等待桶创建完成
            Thread.sleep(500);

            // 2. 尝试设置桶的权限策略
            setBucketPublicReadPolicy(client, bucketName);
            log.info("桶创建配置完成: {} - {}", serviceName, bucketName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageServiceException("桶创建被中断", e);
        } catch (Exception e) {
            log.error("创建桶失败: {} - {}", serviceName, bucketName, e);
            throw new StorageServiceException("创建桶失败: " + e.getMessage(), e);
        }
    }


    /**
     * 检查服务是否支持桶策略
     */
    private boolean isServiceSupportsBucketPolicy(String serviceName) {
        S3StorageProperties.S3ServiceConfig config = storageProperties.getServices().get(serviceName);
        if (config == null) {
            return false;
        }

        // 根据endpoint判断是否为AWS S3或支持策略的服务
        String endpoint = config.getEndpoint().toLowerCase();

        // AWS S3支持桶策略
        if (endpoint.contains("amazonaws.com")) {
            return true;
        }

        // Cloudflare R2支持桶策略
        if (endpoint.contains("r2.cloudflarestorage.com")) {
            return true;
        }

        // MinIO和其他本地服务通常不支持
        return false;
    }

    /**
     * 确保桶存在，如果不存在则创建
     */
    private void ensureBucketExists(String serviceName, S3Client client, String bucketName) {

        // 检查缓存
        Set<String> serviceBuckets = createdBuckets.computeIfAbsent(serviceName, k -> ConcurrentHashMap.newKeySet());

        if (serviceBuckets.contains(bucketName)) {
            return; // 已经创建过
        }

        try {
            // 检查桶是否存在
            client.headBucket(request -> request.bucket(bucketName));
            log.debug("桶已存在: {} - {}", serviceName, bucketName);

            // 桶存在，加入缓存
            serviceBuckets.add(bucketName);

        } catch (NoSuchBucketException e) {
            // 桶不存在，需要创建
            log.info("桶不存在，开始创建: {} - {}", serviceName, bucketName);
            createBucketWithPolicy(serviceName, client, bucketName);
            serviceBuckets.add(bucketName);

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                // 404也表示桶不存在
                log.info("桶不存在(404)，开始创建: {} - {}", serviceName, bucketName);
                createBucketWithPolicy(serviceName, client, bucketName);
                serviceBuckets.add(bucketName);
            } else {
                log.error("检查桶状态失败: {} - {} - 状态码: {}", serviceName, bucketName, e.statusCode(), e);
                throw new StorageServiceException("检查桶状态失败: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("检查桶状态失败: {} - {}", serviceName, bucketName, e);
            throw new StorageServiceException("检查桶状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成日期格式的桶名
     */
    private String generateDatePathPrefix() {
        LocalDate now = LocalDate.now();
        return now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private ServiceUploadResult uploadToService(String serviceName, S3Client client,
                                                MultipartFile file, String fileName) {
        S3StorageProperties.S3ServiceConfig config = storageProperties.getServices().get(serviceName);

        String bucketName = config.getBucket();
        // 生成日期格式的桶名
        String datePathPrefix = generateDatePathPrefix();


        // 确保桶存在
        ensureBucketExists(serviceName, client, bucketName);

        String key = datePathPrefix + fileName;

        try {
            long fileSize = file.getSize();
            log.info("开始上传文件到 {} - 大小: {}MB", serviceName, fileSize / 1024.0 / 1024.0);

            // 将 MultipartFile 的内容读取到字节数组
            byte[] fileBytes = file.getBytes();

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(fileSize)
                    .build();

            RequestBody requestBody = RequestBody.fromBytes(fileBytes);

            long startTime = System.currentTimeMillis();
            client.putObject(request, requestBody);
            long uploadTime = System.currentTimeMillis() - startTime;
            log.info("文件上传成功到 {}/{} - 耗时: {}秒", serviceName, bucketName, uploadTime / 1000.0);
            // 生成访问 URL
            String url = generateAccessUrl(serviceName, config, key, client);

            return new ServiceUploadResult(serviceName, true, url, "上传成功");

        } catch (Exception e) {
            log.error("Upload to {} failed", serviceName, e);
            throw new StorageServiceException("上传到 " + serviceName + " 失败", e);
        }
    }


    // 获取存储信息（脱敏后的）
    public Map<String, Object> getStorageInfo(String prefix) {
        Map<String, Object> info = new HashMap<>();
        info.put("uploadStrategy", storageProperties.getUploadStrategy());
        info.put("specificTargets", storageProperties.getSpecificTargets());

        Map<String, Object> services = new HashMap<>();
        storageProperties.getServices().forEach((name, config) -> {
            Map<String, Object> serviceInfo = new HashMap<>();
            serviceInfo.put("enabled", config.isEnabled());
            serviceInfo.put("endpoint", config.getEndpoint());
            serviceInfo.put("region", config.getRegion());
            serviceInfo.put("bucket", config.getBucket());
            serviceInfo.put("pathPrefix", prefix);
            // 不暴露敏感信息
            services.put(name, serviceInfo);
        });
        info.put("services", services);

        return info;
    }

    /**
     * 获取服务兼容性信息
     */
    public Map<String, Object> getServiceCompatibility() {
        Map<String, Object> compatibility = new HashMap<>();

        storageProperties.getServices().forEach((name, config) -> {
            if (config.isEnabled()) {
                Map<String, Object> serviceInfo = new HashMap<>();
                serviceInfo.put("supportsBucketPolicy", isServiceSupportsBucketPolicy(name));
                serviceInfo.put("endpoint", config.getEndpoint());
                serviceInfo.put("bucketCreationEnabled", true);
                compatibility.put(name, serviceInfo);
            }
        });

        return compatibility;
    }


    public UploadResponse convertToUploadResponse(UploadResult result) {
        List<UploadResponse.ServiceUploadDetail> details = result.results().stream()
                .map(serviceResult -> UploadResponse.ServiceUploadDetail.builder()
                        .serviceName(serviceResult.serviceName())
                        .success(serviceResult.success())
                        .url(serviceResult.url())
                        .message(serviceResult.message())
                        .build())
                .collect(Collectors.toList());

        UploadResponse.UploadStatistics statistics = UploadResponse.UploadStatistics.builder()
                .totalServices(result.getTotalServices())
                .successCount(result.getSuccessCount())
                .failureCount(result.getFailureCount())
                .uploadTimeMs(result.uploadTimeMs())
                .build();

        return UploadResponse.builder()
                .fileName(result.fileName())
                .successUrls(result.getSuccessfulUrls())
                .uploadDetails(details)
                .statistics(statistics)
                .build();
    }


    /**
     * 删除文件
     */
    public ResponseEntity<R<DeleteResponse>> deleteImage(String fileName, HttpServletRequest request, String prefix) {
        // 1.打印ip
        String clientIp = fileValidationService.getClientIp(request);
        log.info("开始处理文件删除请求 - 文件名: {}, 客户端IP: {}", fileName, clientIp);

        Map<String, S3Client> targetClients = getTargetClients();

        if (targetClients.isEmpty()) {
            throw new StorageConfigurationException("没有可用的存储服务");
        }

        List<CompletableFuture<ServiceDeleteResult>> futures = new ArrayList<>();

        for (Map.Entry<String, S3Client> entry : targetClients.entrySet()) {
            String serviceName = entry.getKey();
            S3Client client = entry.getValue();

            CompletableFuture<ServiceDeleteResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return deleteFromService(serviceName, client, fileName, prefix);
                } catch (Exception e) {
                    log.error("Failed to delete from service: {}", serviceName, e);
                    return new ServiceDeleteResult(serviceName, false, e.getMessage());
                }
            }, executorService);

            futures.add(future);
        }

        List<ServiceDeleteResult> results = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        ).thenApply(v ->
                futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
        ).join();

        DeleteResult deleteResult = new DeleteResult(fileName, results);
        DeleteResponse response = convertToDeleteResponse(deleteResult);
        if (deleteResult.isCompletelyDeleted()) {
            log.info("文件删除成功 - 文件名: {}, 成功服务数: {}/{}",
                    fileName, deleteResult.getSuccessCount(), deleteResult.getTotalServices());
            return ResponseEntity.ok(R.success("文件删除成功", response));
        } else {
            log.warn("文件部分删除失败 - 文件名: {}, 成功服务数: {}/{}",
                    fileName, deleteResult.getSuccessCount(), deleteResult.getTotalServices());
            return ResponseEntity.ok(R.success("文件部分删除成功", response));
        }
    }

    /**
     * 查询文件信息
     */
    public ResponseEntity<R<FileInfoResponse>> getFileInfo(String fileName, String prefix) {
        log.info("查询文件信息 - 文件名: {}", fileName);
        Map<String, S3Client> targetClients = clientManager.getAllEnabledClients();

        if (targetClients.isEmpty()) {
            throw new StorageConfigurationException("没有可用的存储服务");
        }

        List<CompletableFuture<ServiceFileInfoResult>> futures = new ArrayList<>();

        for (Map.Entry<String, S3Client> entry : targetClients.entrySet()) {
            String serviceName = entry.getKey();
            S3Client client = entry.getValue();

            CompletableFuture<ServiceFileInfoResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return getFileInfoFromService(serviceName, client, fileName, prefix);
                } catch (Exception e) {
                    log.debug("File not found in service: {} - {}", serviceName, fileName);
                    return new ServiceFileInfoResult(serviceName, false, null, null, null, null, null);
                }
            }, executorService);

            futures.add(future);
        }

        List<ServiceFileInfoResult> results = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        ).thenApply(v ->
                futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
        ).join();

        FileInfoResult fileInfoResult = new FileInfoResult(fileName, results);
        if (!fileInfoResult.existsInAnyService()) {
            return ResponseEntity.notFound().build();
        }

        FileInfoResponse response = convertToFileInfoResponse(fileInfoResult);

        return ResponseEntity.ok(R.success("文件信息查询成功", response));
    }


    private FileInfoResponse convertToFileInfoResponse(FileInfoResult result) {
        List<FileListResponse.ServiceFileInfo> serviceInfos = result.results().stream()
                .map(serviceResult -> FileListResponse.ServiceFileInfo.builder()
                        .serviceName(serviceResult.serviceName())
                        .exists(serviceResult.exists())
                        .url(serviceResult.url())
                        .fileSize(serviceResult.fileSize())
                        .lastModified(serviceResult.lastModified() != null ?
                                LocalDateTime.ofInstant(serviceResult.lastModified(), java.time.ZoneId.systemDefault()) : null)
                        .etag(serviceResult.etag())
                        .build())
                .collect(Collectors.toList());

        // 从第一个存在的服务获取基本信息
        ServiceFileInfoResult firstExisting = result.results().stream()
                .filter(ServiceFileInfoResult::exists)
                .findFirst()
                .orElse(null);

        return FileInfoResponse.builder()
                .fileName(result.fileName())
                .fileSize(firstExisting != null ? firstExisting.fileSize() : 0L)
                .contentType(firstExisting != null ? firstExisting.contentType() : null)
                .uploadTime(firstExisting != null && firstExisting.lastModified() != null ?
                        LocalDateTime.ofInstant(firstExisting.lastModified(), java.time.ZoneId.systemDefault()) : null)
                .serviceInfos(serviceInfos)
                .existsInAllServices(result.existsInAllServices())
                .build();
    }




    private FileListResponse convertToFileListResponse(FileListResult result) {
        List<FileListResponse.FileItem> files = result.files().stream()
                .map(fileItem -> FileListResponse.FileItem.builder()
                        .fileName(fileItem.getFileName())
                        .fileSize(fileItem.getFileSize())
                        .lastModified(Instant.from(LocalDateTime.ofInstant(fileItem.getLastModified(), java.time.ZoneId.systemDefault())))
                        .availableUrls(fileItem.getAvailableUrls())
                        .build())
                .collect(Collectors.toList());

        return FileListResponse.builder()
                .files(files)
                .totalCount(result.totalCount())
                .hasMore(result.hasMore())
                .nextToken(result.nextToken())
                .build();
    }

    /**
     * 替换文件（修改）
     */
    public ResponseEntity<R<UploadResponse>> replaceImage(String fileName, MultipartFile newFile, HttpServletRequest request, String prefix) {
        // 文件验证
        fileValidationService.validateFile(newFile);
        // 先删除旧文件
        deleteImage(fileName, request, prefix);

        // 上传新文件，使用相同的文件名
        UploadResult uploadResult = upload(newFile, fileName);
        if (!uploadResult.allSuccessfulUpload()) {
            throw new StorageConfigurationException("文件替换失败");
        }

        UploadResponse response = convertToUploadResponse(uploadResult);

        return ResponseEntity.ok(R.success("文件替换成功", response));
    }

    /**
     * 重命名文件
     */
    public ResponseEntity<R<UploadResponse>> renameImage(String oldFileName, String newFileName,String prefix) {
        Map<String, S3Client> targetClients = getTargetClients();
        log.info("开始处理文件重命名请求 - 原文件名: {}, 新文件名: {}", oldFileName, newFileName);

        // 验证新文件名
        if (newFileName == null || newFileName.trim().isEmpty()) {
            throw new FileValidationException("新文件名不能为空");
        }
        if (targetClients.isEmpty()) {
            throw new StorageConfigurationException("没有可用的存储服务");
        }

        List<CompletableFuture<ServiceRenameResult>> futures = new ArrayList<>();

        for (Map.Entry<String, S3Client> entry : targetClients.entrySet()) {
            String serviceName = entry.getKey();
            S3Client client = entry.getValue();

            CompletableFuture<ServiceRenameResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return renameInService(serviceName, client, oldFileName, newFileName, prefix);
                } catch (Exception e) {
                    log.error("Failed to rename in service: {}", serviceName, e);
                    return new ServiceRenameResult(serviceName, false, null, e.getMessage());
                }
            }, executorService);

            futures.add(future);
        }

        List<ServiceRenameResult> results = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        ).thenApply(v ->
                futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
        ).join();
        RenameResult result = new RenameResult(oldFileName, newFileName, results);
        if (!result.isCompletelyRenamed()) {
            throw new StorageServiceException("文件重命名失败");
        }

        // 构造类似上传响应的格式
        UploadResponse response = UploadResponse.builder()
                .fileName(result.newFileName())
                .successUrls(result.getSuccessfulUrls())
                .uploadDetails(result.results().stream()
                        .map(r -> UploadResponse.ServiceUploadDetail.builder()
                                .serviceName(r.serviceName())
                                .success(r.success())
                                .url(r.newUrl())
                                .message(r.message())
                                .build())
                        .collect(Collectors.toList()))
                .statistics(UploadResponse.UploadStatistics.builder()
                        .totalServices(result.results().size())
                        .successCount((int) result.results().stream().filter(r -> r.success()).count())
                        .failureCount((int) result.results().stream().filter(r -> !r.success()).count())
                        .uploadTimeMs(0L)
                        .build())
                .build();

        log.info("文件重命名成功 - 原文件名: {}, 新文件名: {}", oldFileName, newFileName);

        return ResponseEntity.ok(R.success("文件重命名成功", response));
    }

    // 私有辅助方法
    private ServiceDeleteResult deleteFromService(String serviceName, S3Client client, String fileName, String prefix) {
        S3StorageProperties.S3ServiceConfig config = storageProperties.getServices().get(serviceName);
        String key = prefix + fileName;

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key)
                    .build();

            client.deleteObject(request);
            return new ServiceDeleteResult(serviceName, true, "删除成功");

        } catch (Exception e) {
            log.error("Delete from {} failed", serviceName, e);
            return new ServiceDeleteResult(serviceName, false, e.getMessage());
        }
    }

    private ServiceFileInfoResult getFileInfoFromService(String serviceName, S3Client client, String fileName, String prefix) {
        S3StorageProperties.S3ServiceConfig config = storageProperties.getServices().get(serviceName);
        String key = prefix + fileName;

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key)
                    .build();

            HeadObjectResponse response = client.headObject(request);

            String url = generateAccessUrl(serviceName, config, key, client);

            return new ServiceFileInfoResult(
                    serviceName, true, url, response.contentLength(),
                    response.lastModified(), response.contentType(), response.eTag()
            );

        } catch (NoSuchKeyException e) {
            return new ServiceFileInfoResult(serviceName, false, null, null, null, null, null);
        }
    }


    private ServiceRenameResult renameInService(String serviceName, S3Client client,
                                                String oldFileName, String newFileName, String prefix) {
        S3StorageProperties.S3ServiceConfig config = storageProperties.getServices().get(serviceName);
        String oldKey = prefix + oldFileName;
        String newKey = prefix + newFileName;

        try {
            // 复制对象到新键
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(config.getBucket())
                    .sourceKey(oldKey)
                    .destinationBucket(config.getBucket())
                    .destinationKey(newKey)
                    .build();

            client.copyObject(copyRequest);

            // 删除原对象
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(oldKey)
                    .build();

            client.deleteObject(deleteRequest);

            String newUrl = generateAccessUrl(serviceName, config, newKey, client);

            return new ServiceRenameResult(serviceName, true, newUrl, "重命名成功");

        } catch (Exception e) {
            log.error("Rename in {} failed", serviceName, e);
            return new ServiceRenameResult(serviceName, false, null, e.getMessage());
        }
    }


    // 转换方法
    private DeleteResponse convertToDeleteResponse(DeleteResult result) {
        List<DeleteResponse.ServiceDeleteResult> details = result.results().stream()
                .map(serviceResult -> DeleteResponse.ServiceDeleteResult.builder()
                        .serviceName(serviceResult.serviceName())
                        .success(serviceResult.success())
                        .message(serviceResult.message())
                        .build())
                .collect(Collectors.toList());

        DeleteResponse.DeleteStatistics statistics = DeleteResponse.DeleteStatistics.builder()
                .totalServices(result.getTotalServices())
                .successCount(result.getSuccessCount())
                .failureCount(result.getTotalServices() - result.getSuccessCount())
                .completelyDeleted(result.isCompletelyDeleted())
                .build();

        return DeleteResponse.builder()
                .fileName(result.fileName())
                .deleteResults(details)
                .statistics(statistics)
                .build();
    }


}
