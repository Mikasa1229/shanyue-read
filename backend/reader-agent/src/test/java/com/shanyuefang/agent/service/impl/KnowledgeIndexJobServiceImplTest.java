package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.entity.KnowledgeIndexJob;
import com.shanyuefang.agent.mapper.KnowledgeDocumentMapper;
import com.shanyuefang.agent.mapper.KnowledgeIndexJobMapper;
import com.shanyuefang.agent.mapper.ModelUsageMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.dto.IndexChapterDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIndexJobServiceImplTest {
    @Test
    void createsDedicatedDeleteLedgerEntry() {
        KnowledgeIndexJobMapper mapper = mock(KnowledgeIndexJobMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        KnowledgeIndexJobServiceImpl service = service(mapper);

        KnowledgeIndexJob result = service.beginDelete(42L);

        assertEquals(42L, result.getCanonicalBookId());
        assertEquals("BOOK_DELETE", result.getJobType());
        assertEquals("PROCESSING", result.getStatus());
        assertEquals("delete:42", result.getDedupeKey());
        verify(mapper).insert(result);
    }

    @Test
    void completedDeleteDoesNotReopenLedgerEntryOnRedelivery() {
        KnowledgeIndexJobMapper mapper = mock(KnowledgeIndexJobMapper.class);
        KnowledgeIndexJob completed = new KnowledgeIndexJob();
        completed.setId(3L);
        completed.setStatus("COMPLETED");
        completed.setJobType("BOOK_DELETE");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(completed);
        KnowledgeIndexJobServiceImpl service = service(mapper);

        assertSame(completed, service.beginDelete(42L));
        verify(mapper, never()).updateById(any(KnowledgeIndexJob.class));
    }

    @Test
    void embeddingVersionCreatesANewChapterIndexJob() {
        KnowledgeIndexJobMapper mapper = mock(KnowledgeIndexJobMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        AgentProperties properties = new AgentProperties();
        KnowledgeIndexJobServiceImpl service = service(mapper, properties);
        IndexChapterDTO chapter = new IndexChapterDTO();
        chapter.setCanonicalBookId(42L);
        chapter.setChapterIndex(3);
        chapter.setContentVersion("v1");
        chapter.setContent("unchanged chapter");

        KnowledgeIndexJob first = service.begin(chapter);
        properties.setEmbeddingModelVersion("hash-embedding-v2");
        KnowledgeIndexJob second = service.begin(chapter);

        org.junit.jupiter.api.Assertions.assertNotEquals(first.getDedupeKey(), second.getDedupeKey());
        verify(mapper, org.mockito.Mockito.times(2)).insert(any(KnowledgeIndexJob.class));
    }

    private KnowledgeIndexJobServiceImpl service(KnowledgeIndexJobMapper mapper) {
        return service(mapper, new AgentProperties());
    }

    private KnowledgeIndexJobServiceImpl service(KnowledgeIndexJobMapper mapper, AgentProperties properties) {
        return new KnowledgeIndexJobServiceImpl(mapper, mock(KnowledgeDocumentMapper.class), mock(ModelUsageMapper.class), new ObjectMapper(), properties);
    }
}
