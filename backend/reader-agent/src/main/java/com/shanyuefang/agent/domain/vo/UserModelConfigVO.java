package com.shanyuefang.agent.domain.vo;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserModelConfigVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String provider;
    private String model;
    private String keyHint;
    private String baseUrl;
    private Boolean enabled;
    private LocalDateTime updatedAt;
}
