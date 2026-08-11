package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_agent_message")
public class AgentMessage {
    @TableId
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String citationsJson;
    private String bookReferencesJson;
    private String toolTraceJson;
    private String generationStatus;
    private LocalDateTime createdAt;
    private Boolean deleted;
}
