package com.github.anicmv;

import com.github.anicmv.config.S3ClientManager;
import com.github.anicmv.config.S3StorageProperties;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
class AniboxS3ApplicationTests {

    @Resource
    private S3ClientManager clientManager;
    @Resource
    private S3StorageProperties storageProperties;


    @Test
    void contextLoads() {
        Map<String, S3Client> targetClients = getTargetClients();
        for (Map.Entry<String, S3Client> entry : targetClients.entrySet()) {
            String serviceName = entry.getKey();
            S3Client client = entry.getValue();
            client.listBuckets().buckets().forEach(bucket -> System.out.println(bucket.name()));
        }
    }

    private Map<String, S3Client> getTargetClients() {
        Map<String, S3Client> allClients = clientManager.getAllEnabledClients();

        switch (storageProperties.getUploadStrategy()) {
            case FIRST:
                return allClients.entrySet().stream()
                        .findFirst()
                        .map(entry -> Map.of(entry.getKey(), entry.getValue()))
                        .orElse(Collections.emptyMap());

            case ALL:
                return allClients;

            case SPECIFIC:
                Map<String, S3Client> specificClients = new HashMap<>();
                for (String target : storageProperties.getSpecificTargets()) {
                    S3Client client = allClients.get(target);
                    if (client != null) {
                        specificClients.put(target, client);
                    }
                }
                return specificClients;

            default:
                return Collections.emptyMap();
        }
    }
}
