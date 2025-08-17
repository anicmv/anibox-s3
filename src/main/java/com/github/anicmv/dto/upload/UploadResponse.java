package com.github.anicmv.dto.upload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author anicmv
 * @date 2025/8/9 15:56
 * @description 上传响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private String fileName;
    private List<String> successUrls;
    private List<ServiceUploadDetail> uploadDetails;
    private UploadStatistics statistics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceUploadDetail {
        private String serviceName;
        private boolean success;
        private String url;
        private String message;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadStatistics {
        private int totalServices;
        private int successCount;
        private int failureCount;
        private long uploadTimeMs;
    }
}