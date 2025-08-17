package com.github.anicmv.dto.result;

import java.time.Instant;

/**
 * @author anicmv
 * @date 2025/8/9 15:56
 * @description todo
 */
public record ServiceFileInfoResult(String serviceName, boolean exists, String url, Long fileSize, Instant lastModified,
                                    String contentType, String etag) {
}