package com.shanyuefang.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.agent.domain.entity.AgentPromptVersion;
import com.shanyuefang.agent.mapper.AgentPromptVersionMapper;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PromptVersionService {
    public static final String NOVEL_AGENT_POLICY = "novel-agent-policy";
    private final AgentPromptVersionMapper mapper;
    public String activeContent() {
        AgentPromptVersion value = mapper.selectOne(Wrappers.<AgentPromptVersion>lambdaQuery().eq(AgentPromptVersion::getPromptKey, NOVEL_AGENT_POLICY).eq(AgentPromptVersion::getActive, true).last("LIMIT 1"));
        return value == null ? "" : value.getContent();
    }
    @Transactional(rollbackFor = Exception.class)
    public AgentPromptVersion createAndActivate(String content) {
        Integer latest = mapper.selectList(Wrappers.<AgentPromptVersion>lambdaQuery().eq(AgentPromptVersion::getPromptKey, NOVEL_AGENT_POLICY)).stream().map(AgentPromptVersion::getVersionNo).max(Integer::compareTo).orElse(0);
        mapper.update(null, Wrappers.<AgentPromptVersion>lambdaUpdate().eq(AgentPromptVersion::getPromptKey, NOVEL_AGENT_POLICY).set(AgentPromptVersion::getActive, false));
        AgentPromptVersion value = new AgentPromptVersion(); value.setId(SnowflakeIdUtil.next()); value.setPromptKey(NOVEL_AGENT_POLICY); value.setVersionNo(latest + 1); value.setContent(content.trim()); value.setActive(true); value.setCreatedAt(LocalDateTime.now()); mapper.insert(value); return value;
    }
    @Transactional(rollbackFor = Exception.class)
    public AgentPromptVersion activate(long versionId) {
        AgentPromptVersion target = mapper.selectById(versionId);
        if (target == null || !NOVEL_AGENT_POLICY.equals(target.getPromptKey())) {
            throw new IllegalArgumentException("Prompt version not found");
        }
        mapper.update(null, Wrappers.<AgentPromptVersion>lambdaUpdate()
                .eq(AgentPromptVersion::getPromptKey, NOVEL_AGENT_POLICY).set(AgentPromptVersion::getActive, false));
        target.setActive(true);
        mapper.updateById(target);
        return target;
    }
}
