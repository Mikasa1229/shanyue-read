package com.shanyuefang.agent.messaging;

import com.shanyuefang.agent.domain.entity.KnowledgeIndexJob;
import com.shanyuefang.agent.service.KnowledgeIndexJobService;
import com.shanyuefang.agent.service.KnowledgeService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDeleteListenerTest {
    @Test
    void completedDeleteRedeliveryDoesNotDeleteAgain() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        KnowledgeIndexJobService jobs = mock(KnowledgeIndexJobService.class);
        KnowledgeIndexJob completed = new KnowledgeIndexJob();
        completed.setStatus("COMPLETED");
        when(jobs.beginDelete(42L)).thenReturn(completed);

        new KnowledgeDeleteListener(knowledgeService, jobs).delete(Map.of("canonicalBookId", 42L));

        verify(knowledgeService, never()).deleteBookKnowledge(42L);
        verify(jobs, never()).complete(org.mockito.ArgumentMatchers.anyLong());
    }
}
