package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.dto.StartBookKnowledgeBuildDTO;
import com.shanyuefang.agent.domain.entity.BookKnowledgeBuildTask;
import com.shanyuefang.agent.domain.entity.KnowledgeChunk;
import com.shanyuefang.agent.domain.entity.UserModelConfig;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.feign.CommentPublishFeignClient;
import com.shanyuefang.agent.feign.UserCreditFeignClient;
import com.shanyuefang.agent.mapper.BookKnowledgeBuildTaskMapper;
import com.shanyuefang.agent.mapper.BookKnowledgeChapterCoverageMapper;
import com.shanyuefang.agent.mapper.BookKnowledgeSpaceMapper;
import com.shanyuefang.agent.mapper.KnowledgeChunkMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphNodeMapper;
import com.shanyuefang.agent.mapper.UserModelConfigMapper;
import com.shanyuefang.agent.service.ApiKeyCipher;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookKnowledgeBuildServiceImplTest {
    @Test
    void preparesAVisibleTokenAndCreditEstimateFromIndexedEvidence() {
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        when(chunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chunk(0, "a".repeat(300)), chunk(1, "b".repeat(600))));
        BookKnowledgeBuildServiceImpl service = service(chunkMapper, mock(UserModelConfigMapper.class), mock(BookKnowledgeBuildTaskMapper.class));

        Map<String, Object> result = service.prepare(1L, 9L);

        assertEquals("NOT_BUILT", result.get("status"));
        assertEquals(true, result.get("isPublic"));
        assertEquals(2, result.get("totalChapters"));
        assertEquals(300L, result.get("estimatedInputTokens"));
        assertEquals(1_000L, result.get("estimatedOutputTokens"));
        assertEquals(1, result.get("estimatedCredits"));
        assertEquals(true, result.get("requiresBuild"));
    }

    @Test
    void refusesToStartBeforeAnyChapterEvidenceIsIndexed() {
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        BookKnowledgeBuildTaskMapper taskMapper = mock(BookKnowledgeBuildTaskMapper.class);
        when(chunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        BookKnowledgeBuildServiceImpl service = service(chunkMapper, mock(UserModelConfigMapper.class), taskMapper);
        StartBookKnowledgeBuildDTO dto = new StartBookKnowledgeBuildDTO();
        dto.setModelMode("PLATFORM");

        assertThrows(BusinessException.class, () -> service.start(1L, 9L, dto));
        verify(taskMapper, never()).insert(any(BookKnowledgeBuildTask.class));
    }

    @Test
    void estimatesOnlyTheReaderSelectedInclusiveChapterRange() {
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        when(chunkMapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(chunk(0, "a".repeat(300)), chunk(1, "b".repeat(600))),
                List.of(chunk(0, "a".repeat(300))));
        BookKnowledgeBuildServiceImpl service = service(chunkMapper, mock(UserModelConfigMapper.class), mock(BookKnowledgeBuildTaskMapper.class));

        Map<String, Object> result = service.prepare(1L, 9L, 1, 1);

        assertEquals(1, result.get("startChapter"));
        assertEquals(1, result.get("endChapter"));
        assertEquals(1, result.get("selectedChapters"));
        assertEquals(100L, result.get("estimatedInputTokens"));
    }

    @Test
    void rejectsASelectedRangeOutsideIndexedChapters() {
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        when(chunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chunk(0, "chapter"), chunk(1, "chapter")));
        BookKnowledgeBuildServiceImpl service = service(chunkMapper, mock(UserModelConfigMapper.class), mock(BookKnowledgeBuildTaskMapper.class));

        assertThrows(BusinessException.class, () -> service.prepare(1L, 9L, 1, 3));
    }

    @Test
    void rejectsAnotherUsersPersonalModelBeforeCreatingATask() {
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        BookKnowledgeBuildTaskMapper taskMapper = mock(BookKnowledgeBuildTaskMapper.class);
        UserModelConfigMapper modelMapper = mock(UserModelConfigMapper.class);
        when(chunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(chunk(0, "可构建的章节内容")));
        UserModelConfig model = new UserModelConfig();
        model.setId(7L);
        model.setUserId(2L);
        model.setEnabled(true);
        model.setDeleted(false);
        when(modelMapper.selectById(7L)).thenReturn(model);
        BookKnowledgeBuildServiceImpl service = service(chunkMapper, modelMapper, taskMapper);
        StartBookKnowledgeBuildDTO dto = new StartBookKnowledgeBuildDTO();
        dto.setModelMode("BYOK");
        dto.setModelConfigId(7L);

        assertThrows(BusinessException.class, () -> service.start(1L, 9L, dto));
        verify(taskMapper, never()).insert(any(BookKnowledgeBuildTask.class));
    }

    private KnowledgeChunk chunk(int chapter, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setChapterIndex(chapter);
        chunk.setContent(content);
        return chunk;
    }

    private BookKnowledgeBuildServiceImpl service(KnowledgeChunkMapper chunkMapper, UserModelConfigMapper modelMapper,
                                                   BookKnowledgeBuildTaskMapper taskMapper) {
        return new BookKnowledgeBuildServiceImpl(taskMapper, mock(BookKnowledgeChapterCoverageMapper.class), mock(BookKnowledgeSpaceMapper.class), chunkMapper,
                mock(KnowledgeGraphNodeMapper.class), modelMapper, mock(ApiKeyCipher.class), new AgentProperties(),
                mock(KnowledgeService.class), mock(UserCreditFeignClient.class), mock(CommentPublishFeignClient.class),
                mock(CanonicalBookFeignClient.class));
    }
}
