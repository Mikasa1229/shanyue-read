package com.shanyuefang.agent.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentSessionVO {
    private Long id;
    private String title;
    private String context;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
