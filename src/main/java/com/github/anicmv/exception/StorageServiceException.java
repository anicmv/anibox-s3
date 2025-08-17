package com.github.anicmv.exception;

/**
 * @author anicmv
 * Exception class for handling errors related to storage service operations.
 * This exception is designed to be thrown when an error occurs during the
 * interaction with the storage service, such as failures in uploading or retrieving files.
 * It extends {@link ImageUploadException} and includes a specific error code "STORAGE_SERVICE_ERROR"
 * for easier identification and handling of the error type.
 *
 * @see ImageUploadException
 */
public class StorageServiceException extends ImageUploadException {
    public StorageServiceException(String message) {
        super(message, "STORAGE_SERVICE_ERROR");
    }

    public StorageServiceException(String message, Throwable cause) {
        super(message, "STORAGE_SERVICE_ERROR", cause);
    }
}