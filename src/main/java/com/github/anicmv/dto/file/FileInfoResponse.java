package com.github.anicmv.dto.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class FileInfoResponse {
    private String fileName;
    private long fileSize;
    private String contentType;
    private LocalDateTime uploadTime;
    private List<FileListResponse.ServiceFileInfo> serviceInfos;
    private boolean existsInAllServices;
}



