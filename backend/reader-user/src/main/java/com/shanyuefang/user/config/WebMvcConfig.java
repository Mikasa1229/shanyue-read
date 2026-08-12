package com.shanyuefang.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 仅为历史头像提供只读兼容，新的头像一律写入 MinIO；本配置不会创建目录或写入本地文件。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        List<String> locations = new ArrayList<>();
        locations.add("file:" + uploadPath + "/");

        // Keep avatars uploaded by older local builds readable after the default path changed.
        Path legacyPath = Paths.get("uploads").toAbsolutePath().normalize();
        if (!legacyPath.equals(uploadPath)) locations.add("file:" + legacyPath + "/");
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(locations.toArray(String[]::new));
    }
}
