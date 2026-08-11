package com.shanyuefang.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.entity.ModelUsage;
import com.shanyuefang.agent.mapper.AgentAdminRoleMapper;
import com.shanyuefang.agent.mapper.AgentPromptVersionMapper;
import com.shanyuefang.agent.mapper.ModelUsageMapper;
import com.shanyuefang.agent.service.AgentAdminAccess;
import com.shanyuefang.agent.service.AgentEvaluationService;
import com.shanyuefang.agent.service.KnowledgeIndexJobService;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.agent.service.ModelPricingService;
import com.shanyuefang.agent.service.ModelRouteService;
import com.shanyuefang.agent.service.PromptVersionService;
import com.shanyuefang.agent.service.RecommendationExperimentService;
import com.shanyuefang.agent.service.BookKnowledgeBuildService;
import com.shanyuefang.agent.config.KnowledgeMessagingConfig;
import com.shanyuefang.agent.domain.entity.KnowledgeIndexJob;
import com.shanyuefang.common.result.R;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentAdminControllerTest {
    @Mock private AgentAdminAccess adminAccess;
    @Mock private KnowledgeIndexJobService indexJobService;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private AgentAdminRoleMapper adminRoleMapper;
    @Mock private AgentPromptVersionMapper promptVersionMapper;
    @Mock private PromptVersionService promptVersionService;
    @Mock private ModelRouteService modelRouteService;
    @Mock private ModelPricingService modelPricingService;
    @Mock private ModelUsageMapper modelUsageMapper;
    @Mock private RecommendationExperimentService recommendationExperimentService;
    @Mock private AgentEvaluationService evaluationService;
    @Mock private KnowledgeService knowledgeService;
    @Mock private BookKnowledgeBuildService bookKnowledgeBuildService;

    @InjectMocks private AgentAdminController controller;

    @Test
    void usageBreakdownReturnsBoundedPrivacySafePromptSections() {
        ModelUsage usage = new ModelUsage();
        usage.setCreatedAt(LocalDateTime.now());
        usage.setInputTokens(100);
        usage.setOutputTokens(20);
        usage.setSystemTokens(10);
        usage.setHistoryTokens(11);
        usage.setGraphTokens(12);
        usage.setCommunityTokens(13);
        usage.setEvidenceTokens(14);
        usage.setToolTokens(15);
        usage.setTokenUsageSource("ESTIMATED");
        usage.setStatus("SUCCESS");
        when(modelUsageMapper.selectList(any())).thenReturn(List.of(usage));
        doNothing().when(adminAccess).require(42L);

        R<Map<String, Object>> response = controller.usageBreakdown(42L, 999);

        assertEquals(200, response.getCode());
        Map<String, Object> data = response.getData();
        assertEquals(90, data.get("days"));
        assertEquals(1, data.get("requests"));
        assertEquals(1L, data.get("sectionCompositionRequests"));
        assertEquals(Map.of("system", 10L, "history", 11L, "graph", 12L,
                "community", 13L, "evidence", 14L, "tool", 15L), data.get("sectionTokens"));
        assertEquals(Map.of("traceRequests", 0, "candidateCount", 0L, "evidenceCount", 0L, "localGraphEdgeCount", 0L,
                "communityCardCount", 0L, "communityEscalations", 0L), data.get("retrieval"));
        List<?> recent = (List<?>) data.get("recent");
        assertEquals(1, recent.size());
        Map<?, ?> row = (Map<?, ?>) recent.get(0);
        assertTrue(row.containsKey("graphTokens"));
        assertFalse(row.containsKey("userId"));
        assertFalse(row.containsKey("sessionId"));
        assertFalse(row.containsKey("requestId"));
        assertFalse(row.containsKey("prompt"));
    }

    @Test
    void embeddingRebuildIsEnqueuedInsteadOfRunningInTheWebRequestProcess() {
        doNothing().when(adminAccess).requireAdmin(42L);
        KnowledgeIndexJob job = new KnowledgeIndexJob(); job.setId(88L); job.setStatus("PENDING");
        when(indexJobService.beginEmbeddingRebuild(9L)).thenReturn(job);

        R<Map<String, Object>> response = controller.rebuildEmbeddings(42L, 9L);

        assertEquals(200, response.getCode());
        assertEquals("QUEUED", response.getData().get("status"));
        verify(rabbitTemplate).convertAndSend(KnowledgeMessagingConfig.EXCHANGE,
                KnowledgeMessagingConfig.EMBEDDING_REBUILD_ROUTING_KEY, Map.of("jobId", 88L));
        org.mockito.Mockito.verifyNoInteractions(knowledgeService);
    }
}
