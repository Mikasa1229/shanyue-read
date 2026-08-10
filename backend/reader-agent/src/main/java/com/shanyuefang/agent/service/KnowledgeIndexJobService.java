package com.shanyuefang.agent.service;

import com.shanyuefang.agent.domain.dto.IndexChapterDTO;
import com.shanyuefang.agent.domain.entity.KnowledgeIndexJob;

import java.util.List;
import java.util.Map;

public interface KnowledgeIndexJobService {
    KnowledgeIndexJob begin(IndexChapterDTO dto);
    KnowledgeIndexJob beginDelete(long canonicalBookId);
    KnowledgeIndexJob beginEmbeddingRebuild(long canonicalBookId);
    boolean claimEmbeddingRebuild(long jobId);
    void complete(long jobId);
    void fail(long jobId, Exception exception);
    List<KnowledgeIndexJob> recent(int limit);
    Map<String, Object> summary();
    Map<String, Object> prepareRetry(long jobId);
    boolean isDeleteJob(long jobId);
}
