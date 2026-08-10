package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.dto.IndexChapterDTO;
import com.shanyuefang.agent.domain.entity.KnowledgeDocument;
import com.shanyuefang.agent.domain.entity.KnowledgeIndexJob;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.mapper.KnowledgeDocumentMapper;
import com.shanyuefang.agent.mapper.KnowledgeIndexJobMapper;
import com.shanyuefang.agent.mapper.ModelUsageMapper;
import com.shanyuefang.agent.service.KnowledgeIndexJobService;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeIndexJobServiceImpl implements KnowledgeIndexJobService {
    private final KnowledgeIndexJobMapper jobMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final ModelUsageMapper usageMapper;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeIndexJob begin(IndexChapterDTO dto) {
        String dedupeKey = fingerprint(dto);
        KnowledgeIndexJob job = jobMapper.selectOne(Wrappers.<KnowledgeIndexJob>lambdaQuery()
                .eq(KnowledgeIndexJob::getDedupeKey, dedupeKey));
        if (job == null) {
            job = new KnowledgeIndexJob();
            job.setId(SnowflakeIdUtil.next());
            job.setCanonicalBookId(dto.getCanonicalBookId());
            job.setJobType("CHAPTER_INDEX");
            job.setStatus("PROCESSING");
            job.setPayloadJson(write(dto));
            job.setRetryCount(0);
            job.setDedupeKey(dedupeKey);
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            jobMapper.insert(job);
            return job;
        }
        if ("COMPLETED".equals(job.getStatus())) return job;
        job.setStatus("PROCESSING");
        job.setRetryCount((job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1);
        job.setErrorMessage(null);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeIndexJob beginDelete(long canonicalBookId) {
        String dedupeKey = "delete:" + canonicalBookId;
        KnowledgeIndexJob job = jobMapper.selectOne(Wrappers.<KnowledgeIndexJob>lambdaQuery().eq(KnowledgeIndexJob::getDedupeKey, dedupeKey));
        if (job == null) {
            job = new KnowledgeIndexJob(); job.setId(SnowflakeIdUtil.next()); job.setCanonicalBookId(canonicalBookId);
            job.setJobType("BOOK_DELETE"); job.setStatus("PROCESSING"); job.setPayloadJson(write(Map.of("canonicalBookId", canonicalBookId)));
            job.setRetryCount(0); job.setDedupeKey(dedupeKey); job.setCreatedAt(LocalDateTime.now()); job.setUpdatedAt(LocalDateTime.now()); jobMapper.insert(job);
        } else {
            // Acknowledged delete messages may be redelivered; completed deletion is terminal and idempotent.
            if ("COMPLETED".equals(job.getStatus())) return job;
            job.setStatus("PROCESSING"); job.setRetryCount((job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1);
            job.setErrorMessage(null); job.setUpdatedAt(LocalDateTime.now()); jobMapper.updateById(job);
        }
        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeIndexJob beginEmbeddingRebuild(long canonicalBookId) {
        String version = agentProperties.getEmbeddingModelVersion();
        String dedupeKey = "embedding-rebuild:" + canonicalBookId + ":" + version;
        KnowledgeIndexJob job = jobMapper.selectOne(Wrappers.<KnowledgeIndexJob>lambdaQuery()
                .eq(KnowledgeIndexJob::getDedupeKey, dedupeKey));
        if (job == null) {
            job = new KnowledgeIndexJob();
            job.setId(SnowflakeIdUtil.next()); job.setCanonicalBookId(canonicalBookId);
            job.setJobType("EMBEDDING_REBUILD"); job.setStatus("PENDING");
            job.setPayloadJson(write(Map.of("canonicalBookId", canonicalBookId, "embeddingModelVersion", version)));
            job.setRetryCount(0); job.setDedupeKey(dedupeKey); job.setCreatedAt(LocalDateTime.now()); job.setUpdatedAt(LocalDateTime.now());
            jobMapper.insert(job);
            return job;
        }
        if ("COMPLETED".equals(job.getStatus()) || "PROCESSING".equals(job.getStatus()) || "PENDING".equals(job.getStatus())) return job;
        job.setStatus("PENDING");
        job.setRetryCount((job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1);
        job.setErrorMessage(null); job.setUpdatedAt(LocalDateTime.now()); jobMapper.updateById(job);
        return job;
    }

    @Override
    public boolean claimEmbeddingRebuild(long jobId) {
        return jobMapper.claimEmbeddingRebuild(jobId) == 1;
    }

    @Override
    public void complete(long jobId) {
        updateStatus(jobId, "COMPLETED", null);
    }

    @Override
    public void fail(long jobId, Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        updateStatus(jobId, "FAILED", message.substring(0, Math.min(message.length(), 1000)));
    }

    @Override
    public List<KnowledgeIndexJob> recent(int limit) {
        return jobMapper.selectList(Wrappers.<KnowledgeIndexJob>lambdaQuery()
                .orderByDesc(KnowledgeIndexJob::getUpdatedAt).last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    @Override
    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Long> jobs = new LinkedHashMap<>();
        for (Map<String, Object> row : jobMapper.selectMaps(Wrappers.<KnowledgeIndexJob>query()
                .select("status", "COUNT(*) AS total").groupBy("status"))) {
            jobs.put(String.valueOf(row.get("status")), ((Number) row.get("total")).longValue());
        }
        result.put("indexJobs", jobs);
        result.put("readyDocuments", documentMapper.selectCount(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getIndexStatus, "READY")));
        result.put("modelRequests", usageMapper.selectCount(null));
        result.put("recentFailures", recent(20).stream().filter(job -> "FAILED".equals(job.getStatus())).limit(5)
                .map(job -> Map.of("id", job.getId(), "canonicalBookId", job.getCanonicalBookId(),
                        "retryCount", job.getRetryCount(), "errorMessage", job.getErrorMessage())).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> prepareRetry(long jobId) {
        KnowledgeIndexJob job = jobMapper.selectById(jobId);
        if (job == null || !"FAILED".equals(job.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Only a failed index job can be retried");
        }
        job.setStatus("PENDING");
        job.setErrorMessage(null);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        try {
            return objectMapper.readValue(job.getPayloadJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read the stored index job payload", exception);
        }
    }

    @Override
    public boolean isDeleteJob(long jobId) {
        KnowledgeIndexJob job = jobMapper.selectById(jobId);
        return job != null && "BOOK_DELETE".equals(job.getJobType());
    }

    private void updateStatus(long jobId, String status, String errorMessage) {
        jobMapper.update(null, Wrappers.<KnowledgeIndexJob>lambdaUpdate()
                .eq(KnowledgeIndexJob::getId, jobId)
                .set(KnowledgeIndexJob::getStatus, status)
                .set(KnowledgeIndexJob::getErrorMessage, errorMessage)
                .set(KnowledgeIndexJob::getUpdatedAt, LocalDateTime.now()));
    }

    private String write(IndexChapterDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not store the index job payload", exception);
        }
    }
    private String write(Map<String, Object> payload) { try { return objectMapper.writeValueAsString(payload); } catch (Exception exception) { throw new IllegalStateException("Could not store delete job payload", exception); } }

    private String fingerprint(IndexChapterDTO dto) {
        try {
            String value = dto.getCanonicalBookId() + ":" + dto.getChapterIndex() + ":" + dto.getContentVersion()
                    + ":" + agentProperties.getEmbeddingModelVersion() + ":" + dto.getContent();
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : hash) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not fingerprint the index job", exception);
        }
    }
}
