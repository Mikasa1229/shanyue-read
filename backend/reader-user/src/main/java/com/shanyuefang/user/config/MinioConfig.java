package com.shanyuefang.user.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置：启动时自动创建 bucket 并设置公开读取策略
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties props;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
        // 确保 bucket 存在并设置公开读取策略
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(props.getBucket()).build());
                log.info("已创建 MinIO bucket: {}", props.getBucket());
            }
            // 设置 bucket 公开读取策略（允许匿名 GET）
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [{
                        "Effect": "Allow",
                        "Principal": {"AWS": ["*"]},
                        "Action": ["s3:GetObject"],
                        "Resource": ["arn:aws:s3:::%s/*"]
                      }]
                    }
                    """.formatted(props.getBucket());
            client.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(props.getBucket())
                    .config(policy)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO 初始化失败，将降级为本地存储: {}", e.getMessage());
        }
        return client;
    }
}
