package com.github.anicmv.exception;

import com.github.anicmv.dto.response.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * @author anicmv
 * 全局异常处理器，用于统一处理系统中抛出的各种异常。
 * 通过捕获特定类型的异常，并返回对应的HTTP响应状态码和错误信息，
 * 提高了系统的健壮性和用户体验。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FileValidationException.class)
    public ResponseEntity<R<Void>> handleFileValidationException(
            FileValidationException e, HttpServletRequest request) {

        log.warn("文件验证失败: {} - URI: {}", e.getMessage(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(R.error(e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(StorageServiceException.class)
    public ResponseEntity<R<Void>> handleStorageServiceException(
            StorageServiceException e, HttpServletRequest request) {

        log.error("存储服务异常: {} - URI: {}", e.getMessage(), request.getRequestURI(), e);

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(R.error("存储服务暂时不可用，请稍后重试", e.getErrorCode()));
    }

    @ExceptionHandler(StorageConfigurationException.class)
    public ResponseEntity<R<Void>> handleStorageConfigurationException(
            StorageConfigurationException e, HttpServletRequest request) {

        log.error("存储配置异常: {} - URI: {}", e.getMessage(), request.getRequestURI(), e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.error("服务配置异常", e.getErrorCode()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<R<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e, HttpServletRequest request) {

        log.warn("上传文件大小超限: {} - URI: {}", e.getMessage(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(R.error("上传文件大小超出限制", "FILE_SIZE_EXCEEDED"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<R<Void>> handleMultipartException(
            MultipartException e, HttpServletRequest request) {

        log.warn("文件上传异常: {} - URI: {}", e.getMessage(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(R.error("文件上传格式错误", "MULTIPART_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleGenericException(
            Exception e, HttpServletRequest request) {

        log.error("未处理的异常: {} - URI: {}", e.getMessage(), request.getRequestURI(), e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.error("服务内部错误，请联系管理员", "INTERNAL_ERROR"));
    }
}
