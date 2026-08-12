package com.shanyuefang.agent.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.entity.KnowledgeIndexJob;
import com.shanyuefang.agent.service.KnowledgeIndexJobService;
import com.shanyuefang.agent.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.*;

class EmbeddingRebuildListenerTest {
    @Test
    void claimedJobIsRebuiltAndCompleted() throws Exception {
        KnowledgeIndexJobService jobs = mock(KnowledgeIndexJobService.class);
        KnowledgeService knowledge = mock(KnowledgeService.class);
        KnowledgeIndexJob job = new KnowledgeIndexJob(); job.setCanonicalBookId(9L);
        when(jobs.claimEmbeddingRebuild(88L)).thenReturn(true);
        when(jobs.find(88L)).thenReturn(job);
        EmbeddingRebuildListener listener = new EmbeddingRebuildListener(new ObjectMapper(), jobs, knowledge, mock(RabbitTemplate.class));

        listener.onMessage(new Message(new ObjectMapper().writeValueAsBytes(java.util.Map.of("jobId", 88L)), new MessageProperties()));

        verify(knowledge).reembedBookEvidence(9L);
        verify(jobs).complete(88L);
    }

    @Test
    void duplicateDeliveryDoesNotRunTheRebuildAgain() throws Exception {
        KnowledgeIndexJobService jobs = mock(KnowledgeIndexJobService.class);
        KnowledgeService knowledge = mock(KnowledgeService.class);
        when(jobs.claimEmbeddingRebuild(88L)).thenReturn(false);
        EmbeddingRebuildListener listener = new EmbeddingRebuildListener(new ObjectMapper(), jobs, knowledge, mock(RabbitTemplate.class));

        listener.onMessage(new Message(new ObjectMapper().writeValueAsBytes(java.util.Map.of("jobId", 88L)), new MessageProperties()));

        verifyNoInteractions(knowledge);
    }
}
