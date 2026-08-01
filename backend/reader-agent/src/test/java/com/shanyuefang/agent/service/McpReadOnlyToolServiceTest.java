package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class McpReadOnlyToolServiceTest {
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
}
