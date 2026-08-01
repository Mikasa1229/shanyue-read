package com.shanyuefang.agent.service;

import java.util.List;

/** Reranks bounded LightRAG evidence candidates; it does not rank graph communities or whole-book reports. */
public interface RerankerService {
    List<Candidate> rerank(String query, List<Candidate> candidates, int limit);

    /**
     * Rerank with a stable rollout subject. Providers can use this subject to select a
     * persisted model route without leaking user identity into the provider request.
     */
    default List<Candidate> rerank(String query, List<Candidate> candidates, int limit, long rolloutSubject) {
        return rerank(query, candidates, limit);
    }

    record Candidate(String content, double retrievalScore, String sources) { }
}
