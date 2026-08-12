package com.shanyuefang.novel.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {
    private final MinioProperties properties;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        try {
            String bucket = properties.getBucket();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建 MinIO bucket: {}", bucket);
            }
            String policy = """
                    {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                    """.formatted(bucket);
            client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
        } catch (Exception exception) {
            // The snapshot service keeps returning the source URL if MinIO is temporarily unavailable.
            log.warn("MinIO 初始化失败，封面将暂时使用书源地址: {}", exception.getMessage());
        }
        return client;
    }
}
