package com.github.anicmv.dto.file;

import com.github.anicmv.dto.result.ServiceFileInfoResult;

import java.util.List;

/**
 * @author anicmv
 */
public record FileInfoResult(String fileName, List<ServiceFileInfoResult> results) {

    public boolean existsInAnyService() {
        return results.stream().anyMatch(ServiceFileInfoResult::exists);
    }

    public boolean existsInAllServices() {
        return results.stream().allMatch(ServiceFileInfoResult::exists);
    }
}