package com.shanyuefang.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Privacy-safe retrieval summary persisted with a model request. It deliberately contains
 * counts, chapter numbers and routing outcomes, never the question, prompt or novel text.
 */
public record RetrievalTrace(
        Long canonicalBookId,
        Integer readingBoundaryChapter,
        int evidenceCount,
        List<Integer> evidenceChapters,
        int candidateCount,
        int selectedCount,
        Map<String, Integer> sourceCandidateCounts,
        int localGraphEdgeCount,
        int communityCardCount,
        boolean communityEscalated,
        Map<String, Integer> promptSectionTokens) {

    public RetrievalTrace {
        evidenceChapters = evidenceChapters == null ? List.of() : List.copyOf(evidenceChapters);
        sourceCandidateCounts = sourceCandidateCounts == null ? Map.of() : Map.copyOf(sourceCandidateCounts);
        promptSectionTokens = promptSectionTokens == null ? Map.of() : Map.copyOf(promptSectionTokens);
        evidenceCount = Math.max(0, evidenceCount);
        candidateCount = Math.max(0, candidateCount);
        selectedCount = Math.max(0, selectedCount);
        localGraphEdgeCount = Math.max(0, localGraphEdgeCount);
        communityCardCount = Math.max(0, communityCardCount);
    }

    public String toJson(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize retrieval trace", exception);
        }
    }

    /** Admin aggregation only needs primitive counters and does not expose persisted text. */
    public Map<String, Object> asMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("canonicalBookId", canonicalBookId);
        value.put("readingBoundaryChapter", readingBoundaryChapter);
        value.put("evidenceCount", evidenceCount);
        value.put("evidenceChapters", evidenceChapters);
        value.put("candidateCount", candidateCount);
        value.put("selectedCount", selectedCount);
        value.put("sourceCandidateCounts", sourceCandidateCounts);
        value.put("localGraphEdgeCount", localGraphEdgeCount);
        value.put("communityCardCount", communityCardCount);
        value.put("communityEscalated", communityEscalated);
        value.put("promptSectionTokens", promptSectionTokens);
        return value;
    }
}
