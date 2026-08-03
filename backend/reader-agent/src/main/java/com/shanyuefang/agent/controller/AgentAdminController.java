package com.shanyuefang.agent.controller;

import com.shanyuefang.agent.service.AgentAdminAccess;
import com.shanyuefang.agent.domain.dto.AgentAdminRoleDTO;
import com.shanyuefang.agent.domain.dto.AgentPromptVersionDTO;
import com.shanyuefang.agent.domain.entity.AgentAdminRole;
import com.shanyuefang.agent.domain.entity.AgentPromptVersion;
import com.shanyuefang.agent.mapper.AgentAdminRoleMapper;
import com.shanyuefang.agent.mapper.AgentPromptVersionMapper;
import com.shanyuefang.agent.service.PromptVersionService;
import com.shanyuefang.agent.service.KnowledgeIndexJobService;
import com.shanyuefang.agent.service.ModelRouteService;
import com.shanyuefang.agent.service.ModelPricingService;
import com.shanyuefang.agent.domain.entity.AgentModelRoute;
import com.shanyuefang.agent.domain.dto.AgentModelRouteDTO;
import com.shanyuefang.agent.domain.entity.AgentModelPricing;
import com.shanyuefang.agent.domain.dto.AgentModelPricingDTO;
import com.shanyuefang.agent.domain.dto.RecommendationExperimentDTO;
import com.shanyuefang.agent.domain.entity.RecommendationExperiment;
import com.shanyuefang.agent.service.RecommendationExperimentService;
import com.shanyuefang.agent.service.AgentEvaluationService;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.agent.domain.dto.AgentAnswerEvaluationDTO;
import com.shanyuefang.agent.domain.dto.GraphClaimReviewDTO;
import com.shanyuefang.agent.domain.vo.GraphReviewClaimVO;
import com.shanyuefang.agent.domain.entity.AgentEvaluationRun;
import com.shanyuefang.agent.domain.entity.AgentEvaluationCaseResult;
import com.shanyuefang.agent.domain.entity.ModelUsage;
import com.shanyuefang.agent.mapper.ModelUsageMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.agent.config.KnowledgeMessagingConfig;
import com.shanyuefang.agent.domain.vo.KnowledgeIndexJobVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.shanyuefang.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import com.shanyuefang.common.result.ResultCode;
import jakarta.validation.Valid;

/** Public admin API boundary; the UI can be added without exposing internal-token operations. */
@RestController
@RequestMapping("/api/agent/admin")
@RequiredArgsConstructor
public class AgentAdminController {
    private final AgentAdminAccess adminAccess;
    private final KnowledgeIndexJobService indexJobService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AgentAdminRoleMapper adminRoleMapper;
    private final AgentPromptVersionMapper promptVersionMapper;
    private final PromptVersionService promptVersionService;
    private final ModelRouteService modelRouteService;
    private final ModelPricingService modelPricingService;
    private final ModelUsageMapper modelUsageMapper;
    private final RecommendationExperimentService recommendationExperimentService;
    private final AgentEvaluationService evaluationService;
    private final KnowledgeService knowledgeService;
    @GetMapping("/overview")
    public R<Map<String, Object>> overview(@RequestHeader("X-User-Id") long userId) {
        adminAccess.require(userId);
        return R.ok(indexJobService.summary());
    }
    @GetMapping("/index-jobs")
    public R<List<KnowledgeIndexJobVO>> jobs(@RequestHeader("X-User-Id") long userId, @RequestParam(defaultValue = "30") int limit) {
        adminAccess.require(userId);
        return R.ok(indexJobService.recent(Math.min(limit, 100)).stream().map(job -> {
            KnowledgeIndexJobVO value = new KnowledgeIndexJobVO(); value.setId(job.getId()); value.setCanonicalBookId(job.getCanonicalBookId());
            value.setJobType(job.getJobType()); value.setStatus(job.getStatus()); value.setRetryCount(job.getRetryCount());
            value.setErrorMessage(job.getErrorMessage()); value.setCreatedAt(job.getCreatedAt()); value.setUpdatedAt(job.getUpdatedAt()); return value;
        }).toList());
    }
    @PostMapping("/index-jobs/{jobId}/retry")
    public R<Void> retry(@RequestHeader("X-User-Id") long userId, @PathVariable long jobId) {
        adminAccess.require(userId);
        Map<String, Object> payload = indexJobService.prepareRetry(jobId);
        rabbitTemplate.convertAndSend(KnowledgeMessagingConfig.EXCHANGE,
                indexJobService.isDeleteJob(jobId) ? KnowledgeMessagingConfig.DELETE_ROUTING_KEY : KnowledgeMessagingConfig.ROUTING_KEY, payload);
        return R.ok();
    }
    @PostMapping("/books/{canonicalBookId}/graph:rebuild")
    public R<Void> rebuildGraph(@RequestHeader("X-User-Id") long userId, @PathVariable long canonicalBookId) {
        adminAccess.require(userId);
        if (canonicalBookId <= 0) return R.fail(ResultCode.PARAM_ERROR, "Canonical book ID must be positive");
        knowledgeService.rebuildGraph(canonicalBookId);
        return R.ok();
    }
    @GetMapping("/books/{canonicalBookId}/graph-review-claims")
    public R<List<GraphReviewClaimVO>> graphReviewClaims(@RequestHeader("X-User-Id") long userId, @PathVariable long canonicalBookId,
                                                           @RequestParam(defaultValue = "30") int limit) {
        adminAccess.require(userId);
        return R.ok(knowledgeService.graphReviewClaims(canonicalBookId, limit));
    }
    @PostMapping("/books/{canonicalBookId}/graph-review-claims/review")
    public R<Void> reviewGraphClaim(@RequestHeader("X-User-Id") long userId, @PathVariable long canonicalBookId,
                                    @Valid @org.springframework.web.bind.annotation.RequestBody GraphClaimReviewDTO dto) {
        adminAccess.require(userId);
        knowledgeService.reviewGraphClaim(canonicalBookId, dto.getClaimType(), dto.getClaimId(), dto.getReviewStatus());
        return R.ok();
    }
    @GetMapping("/roles")
    public R<List<AgentAdminRole>> roles(@RequestHeader("X-User-Id") long userId) {
        adminAccess.requireAdmin(userId);
        return R.ok(adminRoleMapper.selectList(null));
    }
    @PostMapping("/roles")
    public R<Void> grantRole(@RequestHeader("X-User-Id") long userId, @Valid @org.springframework.web.bind.annotation.RequestBody AgentAdminRoleDTO dto) {
        adminAccess.requireAdmin(userId);
        AgentAdminRole role = adminRoleMapper.selectById(dto.getUserId());
        if (role == null) { role = new AgentAdminRole(); role.setUserId(dto.getUserId()); role.setCreatedAt(LocalDateTime.now()); }
        role.setRoleCode(dto.getRoleCode()); role.setUpdatedAt(LocalDateTime.now());
        if (adminRoleMapper.selectById(role.getUserId()) == null) adminRoleMapper.insert(role); else adminRoleMapper.updateById(role);
        return R.ok();
    }
    @org.springframework.web.bind.annotation.DeleteMapping("/roles/{targetUserId}")
    public R<Void> revokeRole(@RequestHeader("X-User-Id") long userId, @PathVariable long targetUserId) {
        adminAccess.requireAdmin(userId);
        if (userId == targetUserId) return R.fail(ResultCode.PARAM_ERROR, "Cannot revoke your own Agent administrator role");
        adminRoleMapper.deleteById(targetUserId);
        return R.ok();
    }
    @GetMapping("/prompt-versions")
    public R<List<AgentPromptVersion>> promptVersions(@RequestHeader("X-User-Id") long userId) {
        adminAccess.requireAdmin(userId);
        return R.ok(promptVersionMapper.selectList(null));
    }
    @PostMapping("/prompt-versions")
    public R<AgentPromptVersion> createPromptVersion(@RequestHeader("X-User-Id") long userId, @Valid @org.springframework.web.bind.annotation.RequestBody AgentPromptVersionDTO dto) {
        adminAccess.requireAdmin(userId);
        return R.ok(promptVersionService.createAndActivate(dto.getContent()));
    }
    @PostMapping("/prompt-versions/{versionId}/activate")
    public R<AgentPromptVersion> activatePromptVersion(@RequestHeader("X-User-Id") long userId, @PathVariable long versionId) {
        adminAccess.requireAdmin(userId);
        return R.ok(promptVersionService.activate(versionId));
    }
    @GetMapping("/model-routes")
    public R<List<AgentModelRoute>> modelRoutes(@RequestHeader("X-User-Id") long userId) {
        adminAccess.requireAdmin(userId); return R.ok(modelRouteService.list());
    }
    @PostMapping("/model-routes")
    public R<AgentModelRoute> saveModelRoute(@RequestHeader("X-User-Id") long userId, @Valid @org.springframework.web.bind.annotation.RequestBody AgentModelRouteDTO dto) {
        adminAccess.requireAdmin(userId); return R.ok(modelRouteService.save(userId, dto));
    }
    @GetMapping("/model-pricing")
    public R<List<AgentModelPricing>> modelPricing(@RequestHeader("X-User-Id") long userId) {
        adminAccess.requireAdmin(userId); return R.ok(modelPricingService.list());
    }
    @PostMapping("/model-pricing")
    public R<AgentModelPricing> saveModelPricing(@RequestHeader("X-User-Id") long userId, @Valid @org.springframework.web.bind.annotation.RequestBody AgentModelPricingDTO dto) {
        adminAccess.requireAdmin(userId); return R.ok(modelPricingService.save(userId, dto));
    }
    @GetMapping("/usage-summary")
    public R<Map<String, Object>> usageSummary(@RequestHeader("X-User-Id") long userId, @RequestParam(defaultValue = "7") int days) {
        adminAccess.require(userId);
        List<ModelUsage> values = modelUsageMapper.selectList(Wrappers.<ModelUsage>lambdaQuery()
                .ge(ModelUsage::getCreatedAt, LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 90)))));
        long input = values.stream().mapToLong(value -> value.getInputTokens() == null ? 0 : value.getInputTokens()).sum();
        long output = values.stream().mapToLong(value -> value.getOutputTokens() == null ? 0 : value.getOutputTokens()).sum();
        long platformCostMicros = values.stream().mapToLong(value -> value.getPlatformCostMicros() == null ? 0 : value.getPlatformCostMicros()).sum();
        long degraded = values.stream().filter(value -> "DEGRADED".equals(value.getStatus())).count();
        return R.ok(Map.of("requests", values.size(), "inputTokens", input, "outputTokens", output,
                "estimatedTokens", input + output, "platformCostMicros", platformCostMicros, "degraded", degraded,
                "providerReportedRequests", values.stream().filter(value -> "PROVIDER".equals(value.getTokenUsageSource())).count(),
                "estimatedUsageRequests", values.stream().filter(value -> !"PROVIDER".equals(value.getTokenUsageSource())).count(),
                "platformRequests", values.stream().filter(value -> "PLATFORM".equals(value.getAccessMode())).count(),
                "byokRequests", values.stream().filter(value -> "BYOK".equals(value.getAccessMode())).count()));
    }
    /**
     * Exposes privacy-safe prompt-section totals so operators can verify the
     * LightRAG token budget without retaining prompt text or user identifiers.
     */
    @GetMapping("/usage-breakdown")
    public R<Map<String, Object>> usageBreakdown(@RequestHeader("X-User-Id") long userId,
                                                  @RequestParam(defaultValue = "7") int days) {
        adminAccess.require(userId);
        int boundedDays = Math.max(1, Math.min(days, 90));
        List<ModelUsage> values = modelUsageMapper.selectList(Wrappers.<ModelUsage>lambdaQuery()
                .ge(ModelUsage::getCreatedAt, LocalDateTime.now().minusDays(boundedDays)));
        Map<String, Long> sections = new LinkedHashMap<>();
        sections.put("system", sum(values, ModelUsage::getSystemTokens));
        sections.put("history", sum(values, ModelUsage::getHistoryTokens));
        sections.put("graph", sum(values, ModelUsage::getGraphTokens));
        sections.put("community", sum(values, ModelUsage::getCommunityTokens));
        sections.put("evidence", sum(values, ModelUsage::getEvidenceTokens));
        sections.put("tool", sum(values, ModelUsage::getToolTokens));
        Map<String, Long> sources = values.stream().collect(Collectors.groupingBy(
                value -> value.getTokenUsageSource() == null ? "ESTIMATED" : value.getTokenUsageSource(),
                LinkedHashMap::new, Collectors.counting()));
        List<Map<String, Object>> recent = values.stream()
                .sorted(Comparator.comparing(ModelUsage::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(50)
                .map(value -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("createdAt", value.getCreatedAt());
                    row.put("inputTokens", safe(value.getInputTokens()));
                    row.put("outputTokens", safe(value.getOutputTokens()));
                    row.put("graphTokens", safe(value.getGraphTokens()));
                    row.put("communityTokens", safe(value.getCommunityTokens()));
                    row.put("evidenceTokens", safe(value.getEvidenceTokens()));
                    row.put("toolTokens", safe(value.getToolTokens()));
                    row.put("tokenUsageSource", value.getTokenUsageSource());
                    row.put("status", value.getStatus());
                    row.put("retrieval", retrievalSummary(value));
                    return row;
                }).toList();
        List<Map<String, Object>> retrieval = values.stream().map(this::retrievalSummary)
                .filter(value -> Boolean.TRUE.equals(value.get("available"))).toList();
        long retrievalEvidence = retrieval.stream().mapToLong(value -> ((Number) value.getOrDefault("evidenceCount", 0)).longValue()).sum();
        long retrievalCandidates = retrieval.stream().mapToLong(value -> ((Number) value.getOrDefault("candidateCount", 0)).longValue()).sum();
        long retrievalGraphEdges = retrieval.stream().mapToLong(value -> ((Number) value.getOrDefault("localGraphEdgeCount", 0)).longValue()).sum();
        long retrievalCommunities = retrieval.stream().mapToLong(value -> ((Number) value.getOrDefault("communityCardCount", 0)).longValue()).sum();
        long retrievalEscalations = retrieval.stream().filter(value -> Boolean.TRUE.equals(value.get("communityEscalated"))).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", boundedDays);
        result.put("requests", values.size());
        result.put("sectionTokens", sections);
        result.put("sectionCompositionRequests", values.stream().filter(this::hasPromptComposition).count());
        result.put("tokenUsageSources", sources);
        result.put("retrieval", Map.of("traceRequests", retrieval.size(), "candidateCount", retrievalCandidates,
                "evidenceCount", retrievalEvidence,
                "localGraphEdgeCount", retrievalGraphEdges, "communityCardCount", retrievalCommunities,
                "communityEscalations", retrievalEscalations));
        result.put("recent", recent);
        return R.ok(result);
    }

    /** Allowlist retrieval counters before exposing them to the administrator UI. */
    private Map<String, Object> retrievalSummary(ModelUsage usage) {
        if (usage == null || usage.getRetrievalTraceJson() == null || usage.getRetrievalTraceJson().isBlank()) {
            return Map.of("available", false);
        }
        try {
            JsonNode node = objectMapper.readTree(usage.getRetrievalTraceJson());
            if (node == null || !node.isObject()) return Map.of("available", false);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("available", true);
            value.put("canonicalBookId", node.path("canonicalBookId").asLong(0));
            value.put("readingBoundaryChapter", node.path("readingBoundaryChapter").asInt(-1));
            value.put("evidenceCount", Math.max(0, node.path("evidenceCount").asInt(0)));
            value.put("candidateCount", Math.max(0, node.path("candidateCount").asInt(0)));
            value.put("selectedCount", Math.max(0, node.path("selectedCount").asInt(0)));
            value.put("localGraphEdgeCount", Math.max(0, node.path("localGraphEdgeCount").asInt(0)));
            value.put("communityCardCount", Math.max(0, node.path("communityCardCount").asInt(0)));
            value.put("communityEscalated", node.path("communityEscalated").asBoolean(false));
            List<Integer> chapters = new java.util.ArrayList<>();
            JsonNode chapterNode = node.path("evidenceChapters");
            if (chapterNode.isArray()) chapterNode.elements().forEachRemaining(item -> {
                if (chapters.size() < 20 && item.canConvertToInt()) chapters.add(Math.max(0, item.asInt()));
            });
            value.put("evidenceChapters", chapters);
            Map<String, Integer> sourceCounts = new LinkedHashMap<>();
            JsonNode sourceNode = node.path("sourceCandidateCounts");
            if (sourceNode.isObject()) sourceNode.fields().forEachRemaining(entry -> {
                if (sourceCounts.size() < 8 && entry.getValue().canConvertToInt()) sourceCounts.put(entry.getKey(), Math.max(0, entry.getValue().asInt()));
            });
            value.put("sourceCandidateCounts", sourceCounts);
            return value;
        } catch (Exception ignored) {
            return Map.of("available", false);
        }
    }

    private long sum(List<ModelUsage> values, java.util.function.Function<ModelUsage, Integer> getter) {
        return values.stream().map(getter).mapToLong(this::safe).sum();
    }

    private long safe(Integer value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private boolean hasPromptComposition(ModelUsage value) {
        return safe(value.getSystemTokens()) + safe(value.getHistoryTokens()) + safe(value.getGraphTokens())
                + safe(value.getCommunityTokens()) + safe(value.getEvidenceTokens()) + safe(value.getToolTokens()) > 0;
    }
    @GetMapping("/recommendation-experiment") public R<RecommendationExperiment> experiment(@RequestHeader("X-User-Id") long userId) { adminAccess.requireAdmin(userId); return R.ok(recommendationExperimentService.current()); }
    @PostMapping("/recommendation-experiment") public R<RecommendationExperiment> saveExperiment(@RequestHeader("X-User-Id") long userId, @Valid @org.springframework.web.bind.annotation.RequestBody RecommendationExperimentDTO dto) { adminAccess.requireAdmin(userId); return R.ok(recommendationExperimentService.save(dto)); }
    @GetMapping("/recommendation-experiment/metrics") public R<Map<String, Object>> experimentMetrics(@RequestHeader("X-User-Id") long userId, @RequestParam(defaultValue = "7") int days) { adminAccess.requireAdmin(userId); return R.ok(recommendationExperimentService.metrics(days)); }
    @GetMapping("/evaluations") public R<List<AgentEvaluationRun>> evaluations(@RequestHeader("X-User-Id") long userId) { adminAccess.require(userId); return R.ok(evaluationService.recent(20)); }
    @GetMapping("/evaluations/{runId}/cases") public R<List<AgentEvaluationCaseResult>> evaluationCases(@RequestHeader("X-User-Id") long userId, @PathVariable long runId) { adminAccess.require(userId); return R.ok(evaluationService.cases(runId)); }
    @PostMapping("/evaluations/policy-suite") public R<AgentEvaluationRun> runPolicySuite(@RequestHeader("X-User-Id") long userId) { adminAccess.requireAdmin(userId); return R.ok(evaluationService.runPolicySuite(userId)); }
    @PostMapping("/evaluations/answer-suite") public R<AgentEvaluationRun> recordAnswerSuite(@RequestHeader("X-User-Id") long userId, @Valid @org.springframework.web.bind.annotation.RequestBody AgentAnswerEvaluationDTO dto) { adminAccess.requireAdmin(userId); return R.ok(evaluationService.recordAnswerSuite(userId, dto)); }
}
