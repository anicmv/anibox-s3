package com.github.anicmv.exception;

/**
 * @author anicmv
 * @date 2025/8/9 15:56
 * @description 文件验证异常
 */
public class FileValidationException extends ImageUploadException {
    public FileValidationException(String message) {
        super(message, "FILE_VALIDATION_ERROR");
    }
}