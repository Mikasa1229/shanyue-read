package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_model_config")
public class UserModelConfig {
    @TableId
    private Long id;
    private Long userId;
    private String provider;
    private String model;
    private String encryptedApiKey;
    private String baseUrl;
    private String keyHint;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
}
