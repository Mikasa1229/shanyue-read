package com.shanyuefang.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.entity.AgentMessage;
import com.shanyuefang.agent.domain.entity.AgentSession;
import com.shanyuefang.agent.mapper.AgentMessageMapper;
import com.shanyuefang.agent.mapper.AgentSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Applies the configured conversation retention period without touching book or credit data. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentConversationRetentionJob {
    private final AgentProperties properties;
    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;

    @Scheduled(cron = "${AGENT_CONVERSATION_RETENTION_CRON:0 20 3 * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void removeExpiredConversations() {
        int retentionDays = properties.getConversationRetentionDays();
        if (retentionDays <= 0) return;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<Long> sessionIds = sessionMapper.selectList(Wrappers.<AgentSession>lambdaQuery()
                        .eq(AgentSession::getDeleted, false)
                        .lt(AgentSession::getUpdatedAt, cutoff)
                        .orderByAsc(AgentSession::getUpdatedAt)
                        .last("LIMIT 500"))
                .stream().map(AgentSession::getId).toList();
        if (sessionIds.isEmpty()) return;
        messageMapper.delete(Wrappers.<AgentMessage>lambdaQuery().in(AgentMessage::getSessionId, sessionIds));
        sessionMapper.deleteBatchIds(sessionIds);
        log.info("Expired {} Agent conversations older than {} days", sessionIds.size(), retentionDays);
    }
}
