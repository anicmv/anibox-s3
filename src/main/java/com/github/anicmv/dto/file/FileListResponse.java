package com.github.anicmv.dto.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author anicmv
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileListResponse {
    private List<FileItem> files;
    private int totalCount;
    private boolean hasMore;
    private String nextToken;

    /**
     * @author anicmv
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileItem {
        private String fileName;
        private long fileSize;
        private Instant lastModified;
        private List<String> availableUrls;
    }

    /**
     * @author anicmv
     * Represents information about a file in a specific service.
     * This class is used to encapsulate details such as the name of the service, whether the file exists,
     * the URL to access the file, its size, last modified time, and an ETag for versioning.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceFileInfo {
        private String serviceName;
        private boolean exists;
        private String url;
        private Long fileSize;
        private LocalDateTime lastModified;
        private String etag;
    }
}