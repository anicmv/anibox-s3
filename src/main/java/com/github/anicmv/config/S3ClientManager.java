package com.github.anicmv.config;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author anicmv
 * S3ClientManager is a Spring component responsible for managing and initializing
 * S3 clients and presigners based on the provided configuration. It supports multiple
 * S3 services, each with its own set of configurations.
 * This class initializes S3 clients and, optionally, S3 presigners for generating
 * presigned URLs, as specified in the application's properties. It also ensures that
 * all created resources are properly closed when the application context is destroyed.
 */

@Component
public class S3ClientManager {

    @Resource
    private S3StorageProperties storageProperties;

    private final Map<String, S3Client> clients = new ConcurrentHashMap<>();
    private final Map<String, S3Presigner> preSigners = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeClients() {
        storageProperties.getServices().forEach((name, config) -> {
            if (config.isEnabled()) {
                S3Client client = createS3Client(config);
                clients.put(name, client);

                // 如果需要预签名URL，创建presigner
                if (config.isUsePresignedUrl()) {
                    S3Presigner preSigner = createS3Presigner(config);
                    preSigners.put(name, preSigner);
                }
            }
        });
    }

    private S3Client createS3Client(S3StorageProperties.S3ServiceConfig config) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                config.getAccessKey(),
                config.getSecretKey()
        );

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(config.getEndpoint()))
                .serviceConfiguration(s3Config)
                .build();
    }

    private S3Presigner createS3Presigner(S3StorageProperties.S3ServiceConfig config) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                config.getAccessKey(),
                config.getSecretKey()
        );

        return S3Presigner.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(config.getEndpoint()))
                .build();
    }

    public S3Client getClient(String serviceName) {
        return clients.get(serviceName);
    }

    public S3Presigner getPreSigner(String serviceName) {
        return preSigners.get(serviceName);
    }

    public Map<String, S3Client> getAllEnabledClients() {
        return clients;
    }

    @PreDestroy
    public void shutdown() {
        clients.values().forEach(S3Client::close);
        preSigners.values().forEach(S3Presigner::close);
    }
}
