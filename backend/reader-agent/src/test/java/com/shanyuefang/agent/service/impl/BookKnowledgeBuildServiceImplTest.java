package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.dto.StartBookKnowledgeBuildDTO;
import com.shanyuefang.agent.domain.entity.BookKnowledgeBuildTask;
import com.shanyuefang.agent.domain.entity.BookKnowledgeChapterCoverage;
import com.shanyuefang.agent.domain.entity.BookKnowledgeSpace;
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
import com.shanyuefang.common.result.R;
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
import org.mockito.ArgumentCaptor;

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

    @Test
    void includesTheCanonicalBookTitleInTaskListResponses() {
        BookKnowledgeBuildTaskMapper taskMapper = mock(BookKnowledgeBuildTaskMapper.class);
        CanonicalBookFeignClient bookClient = mock(CanonicalBookFeignClient.class);
        BookKnowledgeBuildTask task = new BookKnowledgeBuildTask();
        task.setCanonicalBookId(9L);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        when(bookClient.detail(any(), org.mockito.ArgumentMatchers.eq(9L))).thenReturn(R.ok(Map.of("title", "剑来")));
        BookKnowledgeBuildServiceImpl service = service(mock(KnowledgeChunkMapper.class), mock(UserModelConfigMapper.class), taskMapper, bookClient);

        List<BookKnowledgeBuildTask> tasks = service.myTasks(1L, 10);

        assertEquals("剑来", tasks.get(0).getBookTitle());
    }

    @Test
    void operationalReplacementSynchronizesCoverageAndPreservesTheOwner() {
        BookKnowledgeChapterCoverageMapper coverageMapper = mock(BookKnowledgeChapterCoverageMapper.class);
        BookKnowledgeSpaceMapper spaceMapper = mock(BookKnowledgeSpaceMapper.class);
        BookKnowledgeSpace existing = new BookKnowledgeSpace();
        existing.setCanonicalBookId(9L); existing.setOwnerUserId(42L); existing.setIsPublic(true);
        when(spaceMapper.selectById(9L)).thenReturn(existing);
        when(coverageMapper.selectCount(any(Wrapper.class))).thenReturn(100L);
        KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
        when(chunks.selectList(any(Wrapper.class))).thenReturn(java.util.stream.IntStream.range(0, 100)
                .mapToObj(chapter -> chunk(chapter, "正文")).toList());
        BookKnowledgeBuildServiceImpl service = service(chunks, mock(UserModelConfigMapper.class),
                mock(BookKnowledgeBuildTaskMapper.class), mock(CanonicalBookFeignClient.class), coverageMapper, spaceMapper);

        service.synchronizeCompletedRange(9L, 1, 100, true);

        verify(coverageMapper).delete(any(Wrapper.class));
        verify(coverageMapper, org.mockito.Mockito.times(100)).insertIfAbsent(any(BookKnowledgeChapterCoverage.class));
        ArgumentCaptor<BookKnowledgeSpace> saved = ArgumentCaptor.forClass(BookKnowledgeSpace.class);
        verify(spaceMapper).updateById(saved.capture());
        assertEquals(42L, saved.getValue().getOwnerUserId());
        assertEquals("READY", saved.getValue().getStatus());
        assertEquals(100, saved.getValue().getCompletedChapters());
        assertEquals(100, saved.getValue().getTotalChapters());
    }

    @Test
    void coverageSynchronizationMarksOnlyChaptersThatActuallyHaveIndexedText() {
        KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
        when(chunks.selectList(any(Wrapper.class))).thenReturn(List.of(chunk(0, "第一章"), chunk(2, "第三章")));
        BookKnowledgeChapterCoverageMapper coverageMapper = mock(BookKnowledgeChapterCoverageMapper.class);
        when(coverageMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
        BookKnowledgeSpaceMapper spaces = mock(BookKnowledgeSpaceMapper.class);
        BookKnowledgeSpace existing = new BookKnowledgeSpace(); existing.setCanonicalBookId(9L);
        when(spaces.selectById(9L)).thenReturn(existing);
        BookKnowledgeBuildServiceImpl service = service(chunks, mock(UserModelConfigMapper.class),
                mock(BookKnowledgeBuildTaskMapper.class), mock(CanonicalBookFeignClient.class), coverageMapper, spaces);

        service.synchronizeCompletedRange(9L, 1, 3, false);

        ArgumentCaptor<BookKnowledgeChapterCoverage> rows = ArgumentCaptor.forClass(BookKnowledgeChapterCoverage.class);
        verify(coverageMapper, org.mockito.Mockito.times(2)).insertIfAbsent(rows.capture());
        assertEquals(List.of(0, 2), rows.getAllValues().stream().map(BookKnowledgeChapterCoverage::getChapterIndex).toList());
    }

    @Test
    void fullyCoveredPreparationKeepsTheRequestedRangeAndReportsNoBuild() {
        KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
        when(chunks.selectList(any(Wrapper.class))).thenReturn(List.of(chunk(0, "第一章"), chunk(1, "第二章")));
        BookKnowledgeChapterCoverageMapper coverage = mock(BookKnowledgeChapterCoverageMapper.class);
        BookKnowledgeChapterCoverage first = new BookKnowledgeChapterCoverage(); first.setChapterIndex(0);
        BookKnowledgeChapterCoverage second = new BookKnowledgeChapterCoverage(); second.setChapterIndex(1);
        when(coverage.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        when(coverage.selectCount(any(Wrapper.class))).thenReturn(2L);
        BookKnowledgeBuildServiceImpl service = service(chunks, mock(UserModelConfigMapper.class),
                mock(BookKnowledgeBuildTaskMapper.class), mock(CanonicalBookFeignClient.class), coverage,
                mock(BookKnowledgeSpaceMapper.class));

        Map<String, Object> result = service.prepare(7L, 9L, 1, 2);

        assertEquals(1, result.get("startChapter"));
        assertEquals(2, result.get("endChapter"));
        assertEquals(2, result.get("coveredChapters"));
        assertEquals(0, result.get("selectedChapters"));
        assertEquals(true, result.get("rangeCovered"));
        assertEquals(false, result.get("requiresBuild"));
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

    private BookKnowledgeBuildServiceImpl service(KnowledgeChunkMapper chunkMapper, UserModelConfigMapper modelMapper,
                                                   BookKnowledgeBuildTaskMapper taskMapper, CanonicalBookFeignClient bookClient) {
        return service(chunkMapper, modelMapper, taskMapper, bookClient,
                mock(BookKnowledgeChapterCoverageMapper.class), mock(BookKnowledgeSpaceMapper.class));
    }

    private BookKnowledgeBuildServiceImpl service(KnowledgeChunkMapper chunkMapper, UserModelConfigMapper modelMapper,
                                                   BookKnowledgeBuildTaskMapper taskMapper, CanonicalBookFeignClient bookClient,
                                                   BookKnowledgeChapterCoverageMapper coverageMapper, BookKnowledgeSpaceMapper spaceMapper) {
        return new BookKnowledgeBuildServiceImpl(taskMapper, coverageMapper, spaceMapper, chunkMapper,
                mock(KnowledgeGraphNodeMapper.class), modelMapper, mock(ApiKeyCipher.class), new AgentProperties(),
                mock(KnowledgeService.class), mock(UserCreditFeignClient.class), mock(CommentPublishFeignClient.class), bookClient);
    }
}
