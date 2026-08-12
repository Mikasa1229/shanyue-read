package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_agent_shelf_group")
public class AgentShelfGroup {
    @TableId
    private Long userId;
    private Long canonicalBookId;
    private String groupCode;
    private String groupName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
