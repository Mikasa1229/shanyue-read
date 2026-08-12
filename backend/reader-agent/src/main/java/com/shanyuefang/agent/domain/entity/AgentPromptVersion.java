package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_agent_prompt_version")
public class AgentPromptVersion {
    @TableId private Long id;
    private String promptKey;
    private Integer versionNo;
    private String content;
    private Boolean active;
    private LocalDateTime createdAt;
}
