package com.shanyuefang.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalTraceTest {
    @Test
    void serializesCountsAndBoundariesWithoutQuestionOrEvidenceText() throws Exception {
        RetrievalTrace trace = new RetrievalTrace(358679512818388992L, 30, 3,
                List.of(2, 10), 8, 3, Map.of("MILVUS", 3), 12, 2, false,
                Map.of("evidence", 420, "graph", 80));

        String json = trace.toJson(new ObjectMapper());

        assertThat(json).contains("canonicalBookId", "evidenceCount", "evidenceChapters", "communityCardCount");
        assertThat(json).doesNotContain("question", "promptText", "excerpt", "content");
        assertThat(trace.asMap()).containsEntry("readingBoundaryChapter", 30)
                .containsEntry("localGraphEdgeCount", 12);
    }
}
