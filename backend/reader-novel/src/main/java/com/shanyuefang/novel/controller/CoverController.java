package com.shanyuefang.novel.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/covers")
public class CoverController {

    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> getCover(@PathVariable String fileName) {
        Path file = Path.of(System.getProperty("user.dir"))
                .resolve("../uploads/covers")
                .resolve(fileName)
                .normalize();
        FileSystemResource resource = new FileSystemResource(file);
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = mediaType(fileName);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    private MediaType mediaType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        return MediaType.IMAGE_JPEG;
    }
}
