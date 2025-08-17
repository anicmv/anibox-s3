package com.github.anicmv.dto.file;

import java.util.List;

/**
 * @author anicmv
 */
public record FileListResult(List<FileListResponse.FileItem> files, int totalCount, boolean hasMore, String nextToken) {
}