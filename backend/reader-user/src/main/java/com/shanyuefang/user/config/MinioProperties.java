package com.shanyuefang.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {
    /** MinIO 服务端地址 */
    private String endpoint;
    /** 访问密钥 ID */
    private String accessKey;
    /** 访问密钥 Secret */
    private String secretKey;
    /** 存储桶名称 */
    private String bucket;
    /** 对外暴露的公开访问根 URL */
    private String publicUrl;
}
