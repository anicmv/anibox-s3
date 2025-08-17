package com.github.anicmv.config;

import com.github.anicmv.enums.UploadStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * @author anicmv
 * @date 2025/8/9 15:56
 * @description S3存储配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage.s3")
public class S3StorageProperties {

    private UploadStrategy uploadStrategy = UploadStrategy.FIRST;
    private List<String> specificTargets;
    private Map<String, S3ServiceConfig> services;

    @Data
    public static class S3ServiceConfig {
        private boolean enabled = false;
        private String endpoint;
        private String region;
        private String accessKey;
        private String secretKey;
        private String bucket;
        // 新增公开访问URL模式
        private String publicUrlPattern;
        // 是否使用预签名URL
        private boolean usePresignedUrl = false;
        // 预签名URL过期时间（秒）
        private int presignedUrlExpiry = 3600;
    }
}
