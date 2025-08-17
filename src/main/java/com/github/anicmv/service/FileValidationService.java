package com.github.anicmv.service;

import com.github.anicmv.exception.FileValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author anicmv
 * @date 2025/8/9 15:56
 * @description 文件验证服务
 */
@Slf4j
@Service
public class FileValidationService {

    @Value("${storage.validation.max-file-size:10485760}") // 10MB default
    private long maxFileSize;

    @Value("${storage.validation.allowed-content-types:image/jpeg,image/png,image/gif,image/webp}")
    private String[] allowedContentTypes;

    @Value("${storage.validation.allowed-extensions:.jpg,.jpeg,.png,.gif,.webp}")
    private String[] allowedExtensions;

    @Value("${storage.validation.min-file-size:1024}") // 1KB minimum
    private long minFileSize;

    @Value("${storage.validation.enable-content-validation:true}") // 是否启用文件内容验证
    private boolean enableContentValidation;

    private Set<String> allowedContentTypeSet;
    private Set<String> allowedExtensionSet;

    @PostConstruct
    public void init() {
        allowedContentTypeSet = new HashSet<>(Arrays.asList(allowedContentTypes));
        allowedExtensionSet = new HashSet<>(Arrays.asList(allowedExtensions));
        log.info("文件验证服务初始化完成 - 最大文件大小: {}MB, 最小文件大小: {}KB, 内容验证: {}",
                maxFileSize / 1024 / 1024, minFileSize / 1024, enableContentValidation);
    }

    public String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }


    public void validateFile(MultipartFile file) {
        if (file == null) {
            throw new FileValidationException("文件不能为空");
        }

        validateFileNotEmpty(file);
        validateFileSize(file);
        validateContentType(file);
        validateFileName(file);
        validateFileExtension(file);

        // 文件内容验证（可选）
        if (enableContentValidation) {
            validateFileContent(file);
        }
    }

    private void validateFileNotEmpty(MultipartFile file) {
        if (file.isEmpty()) {
            throw new FileValidationException("上传的文件为空");
        }
    }

    private void validateFileSize(MultipartFile file) {
        long size = file.getSize();

        if (size < minFileSize) {
            throw new FileValidationException(
                    String.format("文件大小不能小于 %d bytes (%.2f KB)",
                            minFileSize, minFileSize / 1024.0)
            );
        }

        if (size > maxFileSize) {
            throw new FileValidationException(
                    String.format("文件大小不能超过 %d bytes (%.2f MB)",
                            maxFileSize, maxFileSize / 1024.0 / 1024.0)
            );
        }
    }

    private void validateContentType(MultipartFile file) {
        String contentType = file.getContentType();

        if (contentType == null || contentType.trim().isEmpty()) {
            throw new FileValidationException("无法确定文件类型");
        }

        if (!allowedContentTypeSet.contains(contentType.toLowerCase())) {
            throw new FileValidationException(
                    String.format("不支持的文件类型: %s, 允许的类型: %s",
                            contentType, String.join(", ", allowedContentTypes))
            );
        }
    }

    private void validateFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();

        if (fileName == null || fileName.trim().isEmpty()) {
            throw new FileValidationException("文件名不能为空");
        }

        // 检查文件名中的危险字符
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new FileValidationException("文件名包含非法字符");
        }

        // 检查文件名长度
        if (fileName.length() > 255) {
            throw new FileValidationException("文件名过长，最大长度255字符");
        }

        // 检查特殊字符
        if (fileName.matches(".*[<>:\"|?*].*")) {
            throw new FileValidationException("文件名包含非法字符");
        }
    }

    private void validateFileExtension(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            return;
        }

        String extension = getFileExtension(fileName).toLowerCase();

        if (extension.isEmpty()) {
            throw new FileValidationException("文件必须包含扩展名");
        }

        if (!allowedExtensionSet.contains(extension)) {
            throw new FileValidationException(
                    String.format("不支持的文件扩展名: %s, 允许的扩展名: %s",
                            extension, String.join(", ", allowedExtensions))
            );
        }
    }

    private void validateFileContent(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             BufferedInputStream bis = new BufferedInputStream(inputStream)) {

            // 标记支持，读取文件头
            if (bis.markSupported()) {
                // 标记前16个字节
                bis.mark(16);
            }

            byte[] header = new byte[16];
            int bytesRead = bis.read(header);

            if (bytesRead < 4) {
                throw new FileValidationException("文件内容异常，无法读取文件头");
            }

            if (!isValidImageHeader(header, file.getContentType())) {
                throw new FileValidationException("文件内容与声明的类型不匹配");
            }

            log.debug("文件内容验证通过 - 文件: {}, 类型: {}", file.getOriginalFilename(), file.getContentType());

        } catch (IOException e) {
            log.error("文件内容验证失败 - 文件: {}", file.getOriginalFilename(), e);
            throw new FileValidationException("文件读取失败，请检查文件是否完整");
        }
    }

    private boolean isValidImageHeader(byte[] header, String contentType) {
        if (contentType == null) {
            return false;
        }

        contentType = contentType.toLowerCase();

        // JPEG文件头验证: FF D8 FF
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF;
        }

        // PNG文件头验证: 89 50 4E 47 0D 0A 1A 0A
        if (contentType.contains("png")) {
            return header[0] == (byte) 0x89 && header[1] == 0x50 &&
                    header[2] == 0x4E && header[3] == 0x47 &&
                    header[4] == 0x0D && header[5] == 0x0A &&
                    header[6] == 0x1A && header[7] == 0x0A;
        }

        // GIF文件头验证: 47 49 46 38 (GIF8)
        if (contentType.contains("gif")) {
            return header[0] == 0x47 && header[1] == 0x49 &&
                    header[2] == 0x46 && header[3] == 0x38;
        }

        // WebP文件头验证: 52 49 46 46 ... 57 45 42 50
        if (contentType.contains("webp")) {
            return header[0] == 0x52 && header[1] == 0x49 &&
                    header[2] == 0x46 && header[3] == 0x46 &&
                    header[8] == 0x57 && header[9] == 0x45 &&
                    header[10] == 0x42 && header[11] == 0x50;
        }

        // 如果是其他支持的图片类型，暂时认为有效
        return contentType.startsWith("image/");
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex) : "";
    }

    /**
     * 获取文件验证统计信息
     */
    public FileValidationInfo getValidationInfo() {
        return FileValidationInfo.builder()
                .maxFileSizeBytes(maxFileSize)
                .minFileSizeBytes(minFileSize)
                .allowedContentTypes(allowedContentTypeSet)
                .allowedExtensions(allowedExtensionSet)
                .contentValidationEnabled(enableContentValidation)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class FileValidationInfo {
        private long maxFileSizeBytes;
        private long minFileSizeBytes;
        private Set<String> allowedContentTypes;
        private Set<String> allowedExtensions;
        private boolean contentValidationEnabled;

        public double getMaxFileSizeMb() {
            return maxFileSizeBytes / 1024.0 / 1024.0;
        }

        public double getMinFileSizeKb() {
            return minFileSizeBytes / 1024.0;
        }
    }
}
