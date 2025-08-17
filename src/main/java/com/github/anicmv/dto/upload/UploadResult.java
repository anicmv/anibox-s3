package com.github.anicmv.dto.upload;

import com.github.anicmv.dto.result.ServiceUploadResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author anicmv
 * @date 2025/8/9 15:56
 * @description 上传结果
 */
public record UploadResult(String fileName, List<ServiceUploadResult> results, long uploadTimeMs) {

    public boolean allSuccessfulUpload() {
        return results.stream().allMatch(ServiceUploadResult::success);
    }

    public List<String> getSuccessfulUrls() {
        return results.stream()
                .filter(ServiceUploadResult::success)
                .map(ServiceUploadResult::url)
                .collect(Collectors.toList());
    }

    public int getSuccessCount() {
        return (int) results.stream().filter(ServiceUploadResult::success).count();
    }

    public int getFailureCount() {
        return results.size() - getSuccessCount();
    }

    public int getTotalServices() {
        return results.size();
    }
}