package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class McpReadOnlyToolServiceTest {

    @Test
    void normalizesExactTitlesForAvailabilityChecks() {
        assertEquals("诡秘之主", McpReadOnlyToolService.normalizedTitle("《诡秘之主》"));
        assertEquals("三体", McpReadOnlyToolService.normalizedTitle("三 体"));
    }
    private final McpReadOnlyToolService tools = new McpReadOnlyToolService(
            mock(NovelShelfFeignClient.class), mock(CanonicalBookFeignClient.class), mock(KnowledgeService.class), new AgentProperties(), mock(SpoilerBoundaryService.class));

    @Test
    void rejectsToolsOutsideTheReadOnlyAllowlist() {
        assertThrows(BusinessException.class, () -> tools.call(7L, "bookshelf.move", Map.of()));
    }

    @Test
    void rejectsInvalidBookAndChapterArgumentsBeforeDataAccess() {
        assertThrows(BusinessException.class, () -> tools.call(7L, "book.detail", Map.of("canonicalBookId", "-1")));
        assertThrows(BusinessException.class, () -> tools.call(7L, "knowledge_graph.query", Map.of("canonicalBookId", 1, "currentChapter", -1)));
        assertThrows(BusinessException.class, () -> tools.call(7L, "reading.state", Map.of("canonicalBookId", 1, "currentChapter", "not-a-number")));
    }

    @Test
    void recognizesNaturalChineseRecommendationRequestsAsBookSearch() {
        assertTrue(AgentReadOnlyToolService.asksForBookSearch("请你帮我推荐一本短篇小说，要在书源里能搜到"));
        assertTrue(AgentReadOnlyToolService.asksForBookSearch("最近不知道读什么"));
        assertTrue(AgentReadOnlyToolService.asksForBookSearch("换一本"));
        assertTrue(AgentReadOnlyToolService.asksForBookSearch("我需要可以直接点击的引用"));
    }
}
