package com.github.anicmv.dto.rename;

import com.github.anicmv.dto.result.ServiceRenameResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author anicmv
 */
public record RenameResult(String oldFileName, String newFileName, List<ServiceRenameResult> results) {

    public boolean isCompletelyRenamed() {
        return results.stream().allMatch(ServiceRenameResult::success);
    }

    public List<String> getSuccessfulUrls() {
        return results.stream()
                .filter(ServiceRenameResult::success)
                .map(ServiceRenameResult::newUrl)
                .collect(Collectors.toList());
    }
}