package com.shanyuefang.agent.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserModelConfigVO {
    private Long id;
    private String provider;
    private String model;
    private String keyHint;
    private String baseUrl;
    private Boolean enabled;
    private LocalDateTime updatedAt;
}
