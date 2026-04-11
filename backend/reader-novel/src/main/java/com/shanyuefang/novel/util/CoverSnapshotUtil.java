package com.shanyuefang.novel.util;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;

@Slf4j
public final class CoverSnapshotUtil {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private CoverSnapshotUtil() {}

    public static String snapshot(String coverUrl) {
        try {
            if (coverUrl == null || coverUrl.isBlank()) return coverUrl;
            if (coverUrl.startsWith("/api/covers/")) return coverUrl;
            if (!(coverUrl.startsWith("http://") || coverUrl.startsWith("https://"))) return coverUrl;

            String ext = extFromUrl(coverUrl);
            String fileName = sha1(coverUrl) + ext;

            Path baseDir = Path.of(System.getProperty("user.dir"))
                    .resolve("../uploads/covers")
                    .normalize();
            Files.createDirectories(baseDir);

            Path file = baseDir.resolve(fileName);
            if (!Files.exists(file)) {
                HttpRequest req = HttpRequest.newBuilder(URI.create(coverUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "Mozilla/5.0")
                        .GET()
                        .build();
                HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() < 200 || resp.statusCode() >= 300 || resp.body() == null || resp.body().length == 0) {
                    return coverUrl;
                }
                if (resp.body().length > 5 * 1024 * 1024) {
                    // 避免超大封面写盘
                    return coverUrl;
                }
                Files.write(file, resp.body(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            return "/api/covers/" + fileName;
        } catch (Exception e) {
            log.debug("封面快照失败: {}", e.getMessage());
            return coverUrl;
        }
    }

    private static String extFromUrl(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".png")) return ".png";
        if (lower.contains(".webp")) return ".webp";
        if (lower.contains(".gif")) return ".gif";
        if (lower.contains(".jpeg")) return ".jpeg";
        if (lower.contains(".jpg")) return ".jpg";
        return ".jpg";
    }

    private static String sha1(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
