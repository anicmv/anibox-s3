package com.github.anicmv.dto.result;

/**
 * @author anicmv
 * Represents the result of a service deletion operation. This record includes
 * the name of the service, whether the deletion was successful, and an optional
 * message that may contain additional information or error details.
 */
public record ServiceDeleteResult(String serviceName, boolean success, String message) {
}