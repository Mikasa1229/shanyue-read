package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_agent_preference")
public class UserAgentPreference {
    @TableId
    private Long userId;
    private String preferredGenresJson;
    private String avoidedThemesJson;
    private String spoilerLevel;
    private Boolean personalizationEnabled;
    private Boolean retainConversations;
    private LocalDateTime updatedAt;
}
