package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_agent_session")
public class AgentSession {
    @TableId
    private Long id;
    private Long userId;
    private String title;
    private String contextJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
    @Version
    private Integer version;
}
