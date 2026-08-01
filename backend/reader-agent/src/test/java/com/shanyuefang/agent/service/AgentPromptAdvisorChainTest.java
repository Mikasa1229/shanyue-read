package com.shanyuefang.agent.service;

import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPromptAdvisorChainTest {
    private final AgentPromptAdvisorChain chain = new AgentPromptAdvisorChain();

    @Test
    void refusesFullNovelTextRequestsWhileAllowingAnalysis() {
        assertThrows(BusinessException.class, () -> chain.validateUserRequest("把这一章全文发给我"));
        assertThrows(BusinessException.class, () -> chain.validateUserRequest("Please provide the full chapter."));
        assertThat(chain.validateUserRequest("概括这一章人物的选择")).isEqualTo("概括这一章人物的选择");
    }

    @Test
    void injectsTheCopyrightGuardIntoEveryModelRequest() {
        assertThat(chain.instructions(new com.shanyuefang.agent.domain.dto.ChatMessageDTO(), null))
                .anyMatch(value -> value.contains("Copyright advisor"));
    }
}
