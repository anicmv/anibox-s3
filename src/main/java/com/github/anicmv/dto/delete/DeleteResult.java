package com.github.anicmv.dto.delete;

import com.github.anicmv.dto.result.ServiceDeleteResult;

import java.util.List;


/**
 * Represents the result of a delete operation, encapsulating information about
 * the file from which services were deleted and the outcome for each service.
 * This record provides methods to query the overall success of the deletion,
 * count the number of successful deletions, and get the total number of services
 * that were targeted for deletion.
 *
 * @param fileName The name of the file from which services were deleted.
 * @param results  A list of {@link ServiceDeleteResult} instances, each representing
 *                 the outcome of a delete attempt for a specific service.
 */
public record DeleteResult(String fileName, List<ServiceDeleteResult> results) {

    public boolean isCompletelyDeleted() {
        return results.stream().allMatch(ServiceDeleteResult::success);
    }

    public int getSuccessCount() {
        return (int) results.stream().filter(ServiceDeleteResult::success).count();
    }

    public int getTotalServices() {
        return results.size();
    }
}