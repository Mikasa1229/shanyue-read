package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_agent_admin_role")
public class AgentAdminRole {
    @TableId
    private Long userId;
    private String roleCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
