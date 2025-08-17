package com.github.anicmv.dto.result;

/**
 * @author anicmv
 * @date 2025/8/9 15:56
 * @description 服务上传结果
 */
public record ServiceUploadResult(String serviceName, boolean success, String url, String message) {
}