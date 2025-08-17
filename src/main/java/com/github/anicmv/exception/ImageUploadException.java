package com.github.anicmv.exception;

import lombok.Getter;

/**
 * @author anicmv
 * Exception class for handling errors related to image upload processes.
 * This exception is designed to be thrown when an error occurs during the
 * uploading of images, such as file validation failures or storage service issues.
 * It extends RuntimeException and includes a specific error code for easier
 * identification and handling of the error type.
 *
 */
@Getter
public class ImageUploadException extends RuntimeException {
    private final String errorCode;

    public ImageUploadException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ImageUploadException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}

