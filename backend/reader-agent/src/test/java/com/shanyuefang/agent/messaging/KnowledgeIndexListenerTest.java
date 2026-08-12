package com.shanyuefang.agent.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.dto.IndexChapterDTO;
import com.shanyuefang.agent.domain.entity.KnowledgeIndexJob;
import com.shanyuefang.agent.feign.ContentVersionFeignClient;
import com.shanyuefang.agent.service.KnowledgeIndexJobService;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.common.result.R;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIndexListenerTest {
    @Test
    void successfulIndexMarksTheAuthoritativeVersionReady() {
        Fixture fixture = fixture();

        fixture.listener.index(payload());

        verify(fixture.jobs).complete(19L);
        verify(fixture.versions).updateStatus(eq("internal-token"), org.mockito.ArgumentMatchers.argThat(body ->
                "READY".equals(body.get("indexStatus")) && "hash-v1".equals(body.get("contentHash"))));
    }

    @Test
    void failedIndexMarksTheAuthoritativeVersionFailedBeforeRetryingMessage() {
        Fixture fixture = fixture();
        RuntimeException failure = new RuntimeException("projection failed");
        doThrow(failure).when(fixture.knowledge).indexChapter(any(IndexChapterDTO.class));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> fixture.listener.index(payload()));

        verify(fixture.jobs).fail(eq(19L), eq(failure));
        verify(fixture.versions).updateStatus(eq("internal-token"), org.mockito.ArgumentMatchers.argThat(body ->
                "FAILED".equals(body.get("indexStatus"))));
    }

    @Test
    void versionCallbackFailureDoesNotRepeatASuccessfulIndex() {
        Fixture fixture = fixture();
        doThrow(new RuntimeException("novel service unavailable")).when(fixture.versions).updateStatus(any(), any());

        fixture.listener.index(payload());

        verify(fixture.knowledge).indexChapter(any(IndexChapterDTO.class));
        verify(fixture.jobs).complete(19L);
        verify(fixture.jobs, never()).fail(eq(19L), any(Exception.class));
    }

    @Test
    void rejectedSourceLedgerCallbackDoesNotReopenAnAlreadyCompletedIndex() {
        Fixture fixture = fixture();
        when(fixture.versions.updateStatus(any(), any())).thenReturn(R.fail(404, "missing version"));

        fixture.listener.index(payload());

        verify(fixture.knowledge).indexChapter(any(IndexChapterDTO.class));
        verify(fixture.jobs).complete(19L);
        verify(fixture.jobs, never()).fail(eq(19L), any(Exception.class));
    }

    private Fixture fixture() {
        KnowledgeService knowledge = mock(KnowledgeService.class);
        KnowledgeIndexJobService jobs = mock(KnowledgeIndexJobService.class);
        ContentVersionFeignClient versions = mock(ContentVersionFeignClient.class);
        KnowledgeIndexJob job = new KnowledgeIndexJob();
        job.setId(19L); job.setStatus("PROCESSING");
        when(jobs.begin(any(IndexChapterDTO.class))).thenReturn(job);
        when(versions.updateStatus(any(), any())).thenReturn(R.ok());
        AgentProperties properties = new AgentProperties();
        properties.setInternalToken("internal-token");
        return new Fixture(new KnowledgeIndexListener(new ObjectMapper(), knowledge, jobs, versions, properties), knowledge, jobs, versions);
    }

    private Map<String, Object> payload() {
        return Map.of("canonicalBookId", 88L, "chapterIndex", 4, "content", "chapter content", "contentVersion", "hash-v1");
    }

    private record Fixture(KnowledgeIndexListener listener, KnowledgeService knowledge, KnowledgeIndexJobService jobs,
                           ContentVersionFeignClient versions) { }
}
