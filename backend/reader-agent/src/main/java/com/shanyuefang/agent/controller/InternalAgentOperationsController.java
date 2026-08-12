package com.shanyuefang.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.KnowledgeMessagingConfig;
import com.shanyuefang.agent.domain.entity.KnowledgeIndexJob;
import com.shanyuefang.agent.domain.vo.KnowledgeIndexJobVO;
import com.shanyuefang.agent.service.AgentInternalAccess;
import com.shanyuefang.agent.service.KnowledgeIndexJobService;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/agent/operations")
@RequiredArgsConstructor
public class InternalAgentOperationsController {
    private final AgentInternalAccess internalAccess;
    private final KnowledgeIndexJobService indexJobService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;

    @GetMapping("/summary")
    public R<Map<String, Object>> summary(@RequestHeader("X-Agent-Internal-Token") String token) {
        internalAccess.require(token);
        return R.ok(indexJobService.summary());
    }

    @GetMapping("/index-jobs")
    public R<List<KnowledgeIndexJobVO>> jobs(@RequestHeader("X-Agent-Internal-Token") String token,
                                               @RequestParam(defaultValue = "30") int limit) {
        internalAccess.require(token);
        return R.ok(indexJobService.recent(limit).stream().map(this::toVO).toList());
    }

    @PostMapping("/index-jobs/{jobId}/retry")
    public R<Void> retry(@RequestHeader("X-Agent-Internal-Token") String token, @PathVariable long jobId) {
        internalAccess.require(token);
        Map<String, Object> payload = indexJobService.prepareRetry(jobId);
        rabbitTemplate.convertAndSend(KnowledgeMessagingConfig.EXCHANGE, routingKey(jobId),
                indexJobService.isEmbeddingRebuildJob(jobId) ? Map.of("jobId", jobId) : payload);
        return R.ok();
    }

    /** Rebuilds deterministic graph claims and LightRAG communities after a bulk chapter import. */
    @PostMapping("/books/{canonicalBookId}/rebuild-graph")
    public R<Void> rebuildGraph(@RequestHeader("X-Agent-Internal-Token") String token,
                                @PathVariable long canonicalBookId) {
        internalAccess.require(token);
        knowledgeService.rebuildGraph(canonicalBookId);
        return R.ok();
    }

    private KnowledgeIndexJobVO toVO(KnowledgeIndexJob job) {
        KnowledgeIndexJobVO value = new KnowledgeIndexJobVO();
        value.setId(job.getId());
        value.setCanonicalBookId(job.getCanonicalBookId());
        value.setJobType(job.getJobType());
        value.setStatus(job.getStatus());
        value.setRetryCount(job.getRetryCount());
        value.setErrorMessage(job.getErrorMessage());
        value.setCreatedAt(job.getCreatedAt());
        value.setUpdatedAt(job.getUpdatedAt());
        return value;
    }

    private String routingKey(long jobId) {
        if (indexJobService.isDeleteJob(jobId)) return KnowledgeMessagingConfig.DELETE_ROUTING_KEY;
        if (indexJobService.isEmbeddingRebuildJob(jobId)) return KnowledgeMessagingConfig.EMBEDDING_REBUILD_ROUTING_KEY;
        return KnowledgeMessagingConfig.ROUTING_KEY;
    }

}
