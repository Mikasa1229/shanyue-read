package com.shanyuefang.agent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.service.AgentMetrics;
import com.shanyuefang.agent.service.ModelRouteService;
import com.shanyuefang.agent.service.RerankerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Optional precision ranking for bounded LightRAG evidence candidates only. */
@Slf4j
@Service
public class ConfiguredRerankerService implements RerankerService {
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final AgentMetrics agentMetrics;
    private final ModelRouteService modelRouteService;
    private final AtomicLong unavailableUntilMillis = new AtomicLong(0L);

    /** Keeps focused unit tests independent from MyBatis while production receives the route store. */
    public ConfiguredRerankerService(AgentProperties properties, ObjectMapper objectMapper, AgentMetrics agentMetrics) {
        this(properties, objectMapper, agentMetrics, null);
    }

    @Autowired
    public ConfiguredRerankerService(AgentProperties properties, ObjectMapper objectMapper,
                                     AgentMetrics agentMetrics, ModelRouteService modelRouteService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.agentMetrics = agentMetrics;
        this.modelRouteService = modelRouteService;
    }

    @Override
    public List<Candidate> rerank(String query, List<Candidate> candidates, int limit) {
        return rerank(query, candidates, limit, query == null ? 0L : query.hashCode());
    }

    @Override
    public List<Candidate> rerank(String query, List<Candidate> candidates, int limit, long rolloutSubject) {
        if (candidates.isEmpty()) return List.of();
        if (!configured()) { agentMetrics.recordReranker("disabled"); return localRerank(query, candidates, limit); }
        if (isCoolingDown()) { agentMetrics.recordReranker("fallback"); return localRerank(query, candidates, limit); }
        List<Candidate> external = externalRerank(query, candidates, limit, rolloutSubject);
        if (external == null) {
            openCooldown();
            agentMetrics.recordReranker("fallback");
            return localRerank(query, candidates, limit);
        }
        unavailableUntilMillis.set(0L);
        agentMetrics.recordReranker("success"); return external;
    }

    private List<Candidate> externalRerank(String query, List<Candidate> candidates, int limit, long rolloutSubject) {
        try {
            String model = selectedModel(rolloutSubject);
            String raw = WebClient.create(properties.getRerankerBaseUrl()).post().uri(properties.getRerankerPath())
                    .contentType(MediaType.APPLICATION_JSON).headers(headers -> headers.setBearerAuth(properties.getRerankerApiKey()))
                    .bodyValue(Map.of("model", model, "query", query,
                            "documents", candidates.stream().map(Candidate::content).toList(), "top_n", Math.min(limit, candidates.size())))
                    .retrieve().bodyToMono(String.class).block(Duration.ofMillis(timeoutMillis()));
            JsonNode results = objectMapper.readTree(raw).path("results");
            if (!results.isArray()) return null;
            List<Candidate> ranked = new java.util.ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            int safeLimit = Math.max(1, Math.min(limit, candidates.size()));
            for (JsonNode result : results) {
                int index = result.path("index").asInt(-1);
                if (index < 0 || index >= candidates.size() || !seen.add(index)) return null;
                ranked.add(candidates.get(index));
                if (ranked.size() >= safeLimit) break;
            }
            return ranked.isEmpty() ? null : ranked;
        } catch (Exception exception) {
            // Do not log request payloads or provider response bodies on an optional ranking failure.
            log.warn("LightRAG evidence reranker unavailable; using local hybrid ranking ({})", exception.getClass().getSimpleName());
            return null;
        }
    }

    private List<Candidate> localRerank(String query, List<Candidate> candidates, int limit) {
        return LocalEvidenceReranker.rank(query, candidates, limit);
    }
    private boolean configured() { return properties.isRerankerEnabled() && StringUtils.hasText(properties.getRerankerBaseUrl()) && StringUtils.hasText(properties.getRerankerApiKey()); }

    private String selectedModel(long rolloutSubject) {
        String fallback = properties.getRerankerModel();
        if (modelRouteService == null) return fallback;
        try {
            return modelRouteService.resolve("RERANKER", rolloutSubject, fallback);
        } catch (Exception exception) {
            log.warn("Reranker model route unavailable; using environment model ({})", exception.getClass().getSimpleName());
            return fallback;
        }
    }

    private boolean isCoolingDown() { return unavailableUntilMillis.get() > System.currentTimeMillis(); }

    private void openCooldown() {
        long cooldown = Math.max(1, Math.min(properties.getRerankerFailureCooldownSeconds(), 600)) * 1000L;
        unavailableUntilMillis.set(System.currentTimeMillis() + cooldown);
    }

    private int timeoutMillis() { return Math.max(100, Math.min(properties.getRerankerOperationTimeoutMillis(), 10_000)); }
}
