package com.github.anicmv.controller;

import com.github.anicmv.dto.delete.DeleteResponse;
import com.github.anicmv.dto.file.FileInfoResponse;
import com.github.anicmv.dto.file.FileListResponse;
import com.github.anicmv.dto.rename.RenameRequest;
import com.github.anicmv.dto.response.R;
import com.github.anicmv.dto.upload.UploadResponse;
import com.github.anicmv.service.FileValidationService;
import com.github.anicmv.service.ImageUploadService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * @author anicmv
 * @date 2025/8/9 15:56
 * @description 图片上传接口
 */
@RestController
public class ImageUploadController {

    @Resource
    private ImageUploadService uploadService;

    @Resource
    private FileValidationService fileValidationService;

    @PostMapping(value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<R<UploadResponse>> uploadImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        return uploadService.uploadImage(file, request);
    }

    @GetMapping("/health")
    public ResponseEntity<R<String>> health() {
        return ResponseEntity.ok(R.success("服务运行正常", "OK"));
    }

    @GetMapping("/info")
    public ResponseEntity<R<Map<String, Object>>> getStorageInfo(@RequestParam(required = false) String prefix) {
        return ResponseEntity.ok(R.success(uploadService.getStorageInfo(prefix)));
    }

    @GetMapping("/validation-info")
    public ResponseEntity<R<FileValidationService.FileValidationInfo>> getValidationInfo() {
        FileValidationService.FileValidationInfo info = fileValidationService.getValidationInfo();
        return ResponseEntity.ok(R.success("文件验证配置信息", info));
    }

    /**
     * 删除图片
     */
    @DeleteMapping("/{fileName}")
    public ResponseEntity<R<DeleteResponse>> deleteImage(
            @PathVariable String fileName,
            @RequestParam String prefix,
            HttpServletRequest request) {
        return uploadService.deleteImage(fileName, request, prefix);
    }

    /**
     * 查询图片信息
     */
    @GetMapping("/{fileName}")
    public ResponseEntity<R<FileInfoResponse>> getFileInfo(@PathVariable String fileName, @RequestParam String prefix) {
        return uploadService.getFileInfo(fileName, prefix);
    }

    /**
     * 列出图片
     */
    @GetMapping("/list")
    public ResponseEntity<R<FileListResponse>> listFiles(
            @RequestParam String prefix,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String token) {
        return uploadService.listFiles(prefix, limit, token);
    }

    /**
     * 替换图片（修改）
     */
    @PutMapping(value = "/{fileName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<R<UploadResponse>> replaceImage(
            @PathVariable String fileName,
            @RequestParam String prefix,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        return uploadService.replaceImage(fileName, file, request, prefix);
    }

    /**
     * 重命名图片
     */
    @PostMapping("/{fileName}/rename")
    public ResponseEntity<R<UploadResponse>> renameImage(
            @PathVariable String fileName,
            @RequestBody RenameRequest request,
            @RequestParam String prefix) {
        return uploadService.renameImage(fileName, request.getNewFileName(), prefix);
    }


    @GetMapping("/bucket-stats")
    public ResponseEntity<R<Object>> getBucketStatistics(@RequestParam String bucketName) {
        Object stats = uploadService.getBucketStatistics(bucketName);
        return ResponseEntity.ok(R.success("桶统计信息", stats));
    }

    @GetMapping("/service-compatibility")
    public ResponseEntity<R<Object>> getServiceCompatibility() {
        Object compatibility = uploadService.getServiceCompatibility();
        return ResponseEntity.ok(R.success("服务兼容性信息", compatibility));
    }
}
