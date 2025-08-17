package com.github.anicmv.exception;

/**
 * @author anicmv
 * Exception class for handling errors related to storage configuration issues.
 * This exception is designed to be thrown when an error occurs due to misconfiguration
 * or missing configuration in the storage setup, such as incorrect settings for a cloud
 * storage service. It extends {@link ImageUploadException} and includes a specific error
 * code for easier identification and handling of the error type.
 *
 * @see ImageUploadException
 */
public class StorageConfigurationException extends ImageUploadException {
    public StorageConfigurationException(String message) {
        super(message, "STORAGE_CONFIG_ERROR");
    }
}