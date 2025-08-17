package com.github.anicmv.enums;

/**
 * @author anicmv
 * 定义了上传文件到S3服务的策略。
 */
public enum UploadStrategy {
        // 上传到第一个可用的S3服务
        FIRST,
        // 上传到所有启用的S3服务
        ALL,
        // 上传到指定的S3服务
        SPECIFIC
    }