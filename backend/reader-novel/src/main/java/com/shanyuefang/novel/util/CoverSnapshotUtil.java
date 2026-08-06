package com.shanyuefang.novel.util;

import com.shanyuefang.novel.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;

/** Downloads external covers into MinIO; this utility never creates a local upload directory. */
@Slf4j
@Component
@RequiredArgsConstructor
public final class CoverSnapshotUtil {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public String snapshot(String coverUrl) {
        try {
            if (coverUrl == null || coverUrl.isBlank()) return coverUrl;
            if (coverUrl.startsWith("/api/covers/") || coverUrl.contains("/reader-assets/")) return coverUrl;
            if (!(coverUrl.startsWith("http://") || coverUrl.startsWith("https://"))) return coverUrl;
            String objectName = "covers/" + sha1(coverUrl) + extFromUrl(coverUrl);
            if (exists(objectName)) return publicUrl(objectName);
            HttpRequest request = HttpRequest.newBuilder(URI.create(coverUrl)).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0").GET().build();
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || body == null || body.length == 0 || body.length > 5 * 1024 * 1024) return coverUrl;
            String contentType = response.headers().firstValue("content-type").orElseGet(() -> contentType(objectName));
            minioClient.putObject(PutObjectArgs.builder().bucket(properties.getBucket()).object(objectName)
                    .stream(new ByteArrayInputStream(body), body.length, -1).contentType(contentType).build());
            return publicUrl(objectName);
        } catch (Exception exception) {
            log.debug("封面上传 MinIO 失败，保留原始地址: {}", exception.getMessage());
            return coverUrl;
        }
    }

    /** Stores a user-selected cover image without ever writing it to the application filesystem. */
    public String upload(byte[] body, String originalName, String contentType) {
        try {
            if (body == null || body.length == 0 || body.length > 5 * 1024 * 1024) {
                throw new IllegalArgumentException("封面图片大小必须在 5MB 以内");
            }
            String objectName = "covers/" + sha1(body) + extFromName(originalName);
            if (!exists(objectName)) {
                String safeContentType = contentType != null && contentType.startsWith("image/")
                        ? contentType : contentType(objectName);
                minioClient.putObject(PutObjectArgs.builder().bucket(properties.getBucket()).object(objectName)
                        .stream(new ByteArrayInputStream(body), body.length, -1).contentType(safeContentType).build());
            }
            return publicUrl(objectName);
        } catch (Exception exception) {
            throw new IllegalStateException("封面图片上传失败", exception);
        }
    }

    private boolean exists(String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(properties.getBucket()).object(objectName).build());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String publicUrl(String objectName) {
        return properties.getPublicUrl() + "/" + properties.getBucket() + "/" + objectName;
    }

    private static String contentType(String name) {
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private static String extFromUrl(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".png")) return ".png";
        if (lower.contains(".webp")) return ".webp";
        if (lower.contains(".gif")) return ".gif";
        if (lower.contains(".jpeg")) return ".jpeg";
        return ".jpg";
    }

    private static String extFromName(String name) {
        if (name == null) return ".jpg";
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return ".png";
        if (lower.endsWith(".webp")) return ".webp";
        if (lower.endsWith(".gif")) return ".gif";
        if (lower.endsWith(".jpeg")) return ".jpeg";
        return ".jpg";
    }

    private static String sha1(byte[] body) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(body);
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format("%02x", value));
        return result.toString();
    }

    private static String sha1(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format("%02x", value));
        return result.toString();
    }
}
