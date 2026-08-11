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
                .anyMatch(value -> value.contains("版权规则"));
    }

    @Test
    void instructsShelfPlansToUseParseableMarkdownInsteadOfInlineFormatting() {
        com.shanyuefang.agent.domain.dto.ChatMessageDTO dto = new com.shanyuefang.agent.domain.dto.ChatMessageDTO();
        dto.setContent("请读取我的书架，整理并分类作品");

        assertThat(chain.instructions(dto, null)).anyMatch(value -> value.contains("标题前后各保留一个空行")
                && value.contains("每本书必须单独一行"));
        assertThat(chain.instructions(dto, null)).anyMatch(value -> value.contains("输出规则：使用规范 Markdown"));
    }
}
