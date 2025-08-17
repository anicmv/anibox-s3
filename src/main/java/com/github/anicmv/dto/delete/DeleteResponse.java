package com.github.anicmv.dto.delete;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


/**
 * @author anicmv
 * Represents the response structure for a delete operation, encapsulating information
 * about the file from which services were deleted, the outcome for each service,
 * and overall statistics of the deletion process.
 *
 * This class is used to provide a detailed report back to the client after a
 * delete request has been processed, including whether the deletion was successful
 * for each targeted service, any messages associated with the deletion outcomes,
 * and aggregated statistics like the total number of services, success count,
 * failure count, and if the deletion was completely successful.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeleteResponse {
    private String fileName;
    private List<ServiceDeleteResult> deleteResults;
    private DeleteStatistics statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceDeleteResult {
        private String serviceName;
        private boolean success;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteStatistics {
        private int totalServices;
        private int successCount;
        private int failureCount;
        private boolean completelyDeleted;
    }
}
