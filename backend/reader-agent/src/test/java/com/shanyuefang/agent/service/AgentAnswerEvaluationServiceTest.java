package com.shanyuefang.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.dto.AgentAnswerEvaluationDTO;
import com.shanyuefang.agent.domain.entity.AgentEvaluationCaseResult;
import com.shanyuefang.agent.domain.entity.AgentEvaluationRun;
import com.shanyuefang.agent.mapper.AgentEvaluationCaseResultMapper;
import com.shanyuefang.agent.mapper.AgentEvaluationRunMapper;
import com.shanyuefang.agent.mapper.KnowledgeChunkMapper;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.common.result.R;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentAnswerEvaluationServiceTest {
    @Test
    void recordsEvidenceForActualAnswersAndRejectsSpoilerBoundaryViolations() {
        AgentEvaluationRunMapper runs = mock(AgentEvaluationRunMapper.class);
        AgentEvaluationCaseResultMapper cases = mock(AgentEvaluationCaseResultMapper.class);
        KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
        CanonicalBookFeignClient canonicalBooks = mock(CanonicalBookFeignClient.class);
        when(runs.insert(any(AgentEvaluationRun.class))).thenReturn(1); when(cases.insert(any(AgentEvaluationCaseResult.class))).thenReturn(1);
        when(chunks.selectCount(any())).thenReturn(1L);
        AgentEvaluationService service = new AgentEvaluationService(runs, cases, chunks, canonicalBooks, new AgentProperties(), new ObjectMapper());
        AgentAnswerEvaluationDTO dto = new AgentAnswerEvaluationDTO(); dto.setModel("test-model"); dto.setPromptVersion("v1");
        AgentAnswerEvaluationDTO.CaseInput citation = input("citation-grounding", "citation", "Chapter three says this.", 3, List.of(3));
        AgentAnswerEvaluationDTO.CaseInput spoiler = input("spoiler-boundary", "spoiler", "The ending is revealed later.", 3, List.of(9));
        dto.setCases(List.of(citation, spoiler));

        assertEquals("FAILED", service.recordAnswerSuite(1L, dto).getStatus());
        verify(cases, org.mockito.Mockito.times(2)).insert(any(AgentEvaluationCaseResult.class));
    }

    @Test
    void rejectsCitationAndRecommendationIdsThatDoNotResolveToIndexedWorks() {
        AgentEvaluationRunMapper runs = mock(AgentEvaluationRunMapper.class);
        AgentEvaluationCaseResultMapper cases = mock(AgentEvaluationCaseResultMapper.class);
        KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
        CanonicalBookFeignClient canonicalBooks = mock(CanonicalBookFeignClient.class);
        when(runs.insert(any(AgentEvaluationRun.class))).thenReturn(1); when(cases.insert(any(AgentEvaluationCaseResult.class))).thenReturn(1);
        when(chunks.selectCount(any())).thenReturn(0L);
        when(canonicalBooks.detail(any(), any(Long.class))).thenReturn(R.ok(java.util.Map.of()));
        AgentEvaluationService service = new AgentEvaluationService(runs, cases, chunks, canonicalBooks, new AgentProperties(), new ObjectMapper());
        AgentAnswerEvaluationDTO dto = new AgentAnswerEvaluationDTO(); dto.setModel("test-model"); dto.setPromptVersion("v1");
        AgentAnswerEvaluationDTO.CaseInput citation = input("citation-grounding", "citation", "A sourced answer.", 3, List.of(3));
        AgentAnswerEvaluationDTO.CaseInput recommendation = input("recommendation-provenance", "recommendation", "Try work 99.", 3, List.of());
        recommendation.setRecommendationBookIds(List.of(99L));
        dto.setCases(List.of(citation, recommendation));

        assertEquals("FAILED", service.recordAnswerSuite(1L, dto).getStatus());
    }

    @Test
    void acceptsRecommendationWhenTheCanonicalWorkExistsOutsideTheAgentIndex() {
        AgentEvaluationRunMapper runs = mock(AgentEvaluationRunMapper.class);
        AgentEvaluationCaseResultMapper cases = mock(AgentEvaluationCaseResultMapper.class);
        KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
        CanonicalBookFeignClient canonicalBooks = mock(CanonicalBookFeignClient.class);
        when(runs.insert(any(AgentEvaluationRun.class))).thenReturn(1); when(cases.insert(any(AgentEvaluationCaseResult.class))).thenReturn(1);
        when(canonicalBooks.detail(any(), any(Long.class))).thenReturn(R.ok(java.util.Map.of("canonicalBookId", 99L, "title", "Canonical work")));
        AgentEvaluationService service = new AgentEvaluationService(runs, cases, chunks, canonicalBooks, new AgentProperties(), new ObjectMapper());
        AgentAnswerEvaluationDTO dto = new AgentAnswerEvaluationDTO(); dto.setModel("test-model"); dto.setPromptVersion("v1");
        AgentAnswerEvaluationDTO.CaseInput recommendation = input("recommendation-provenance", "recommendation", "Try Canonical work.", 3, List.of());
        recommendation.setRecommendationBookIds(List.of(99L));
        dto.setCases(List.of(recommendation));

        assertEquals("PASSED", service.recordAnswerSuite(1L, dto).getStatus());
    }

    private AgentAnswerEvaluationDTO.CaseInput input(String id, String category, String answer, int boundary, List<Integer> citations) {
        AgentAnswerEvaluationDTO.CaseInput value = new AgentAnswerEvaluationDTO.CaseInput(); value.setCaseId(id); value.setCategory(category); value.setPrompt("test prompt");
        value.setAnswer(answer); value.setReadingBoundaryChapter(boundary); value.setCanonicalBookId(42L); value.setCitationChapters(citations); return value;
    }
}
