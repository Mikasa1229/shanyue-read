package com.shanyuefang.agent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.service.AgentMetrics;
import com.shanyuefang.agent.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OpenAI-compatible semantic embeddings with a deterministic local fallback.
 * The fallback keeps indexing and reading available when no provider is configured;
 * switching providers requires a new embedding version and Milvus collection.
 */
@Service
@Primary
public class ConfiguredEmbeddingService implements EmbeddingService {
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final AgentMetrics metrics;
    private final EmbeddingService fallback;
    private final AtomicLong unavailableUntilMillis = new AtomicLong(0L);

    public ConfiguredEmbeddingService(AgentProperties properties, ObjectMapper objectMapper,
                                      AgentMetrics metrics,
                                      @Qualifier("hashEmbeddingService") EmbeddingService fallback) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.fallback = fallback;
    }

    @Override
    public List<Double> embed(String text) {
        List<List<Double>> values = embedAll(List.of(text == null ? "" : text));
        return values.isEmpty() ? fallback.embed(text) : values.get(0);
    }

    @Override
    public List<List<Double>> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        List<String> safeTexts = texts.stream().map(value -> value == null ? "" : value).toList();
        if (!configured()) {
            metrics.recordEmbedding("disabled");
            return fallback.embedAll(safeTexts);
        }
        if (isCoolingDown()) {
            metrics.recordEmbedding("fallback");
            return fallback.embedAll(safeTexts);
        }
        try {
            List<List<Double>> result = callProvider(safeTexts);
            unavailableUntilMillis.set(0L);
            metrics.recordEmbedding("success");
            return result;
        } catch (Exception ignored) {
            openCooldown();
            metrics.recordEmbedding("fallback");
            return fallback.embedAll(safeTexts);
        }
    }

    @Override
    public int dimensions() {
        return configuredDimensions();
    }

    @Override
    public double similarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.size() != right.size()) return 0d;
        double score = 0d;
        for (int index = 0; index < left.size(); index++) {
            Double a = left.get(index);
            Double b = right.get(index);
            if (a == null || b == null || !Double.isFinite(a) || !Double.isFinite(b)) return 0d;
            score += a * b;
        }
        return score;
    }

    private List<List<Double>> callProvider(List<String> texts) throws Exception {
        String raw = WebClient.create(properties.getEmbeddingBaseUrl()).post()
                .uri(properties.getEmbeddingPath())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(properties.getEmbeddingApiKey()))
                .bodyValue(Map.of("model", properties.getEmbeddingModel(), "input", texts))
                .retrieve().bodyToMono(String.class)
                .block(Duration.ofMillis(timeoutMillis()));
        JsonNode data = objectMapper.readTree(raw).path("data");
        if (!data.isArray() || data.size() != texts.size()) throw new IllegalStateException("Invalid embedding response");
        Map<Integer, List<Double>> byIndex = new HashMap<>();
        for (JsonNode item : data) {
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= texts.size() || byIndex.containsKey(index)) {
                throw new IllegalStateException("Invalid embedding index");
            }
            JsonNode vector = item.path("embedding");
            if (!vector.isArray() || vector.size() != configuredDimensions()) throw new IllegalStateException("Embedding dimension mismatch");
            List<Double> numbers = new ArrayList<>(vector.size());
            for (JsonNode number : vector) {
                double value = number.asDouble(Double.NaN);
                if (!Double.isFinite(value)) throw new IllegalStateException("Invalid embedding number");
                numbers.add(value);
            }
            byIndex.put(index, numbers);
        }
        List<List<Double>> values = new ArrayList<>(texts.size());
        for (int index = 0; index < texts.size(); index++) {
            List<Double> vector = byIndex.get(index);
            if (vector == null) throw new IllegalStateException("Missing embedding index");
            values.add(vector);
        }
        return values;
    }

    private boolean configured() {
        String provider = properties.getEmbeddingProvider();
        return provider != null
                && (provider.equalsIgnoreCase("openai") || provider.equalsIgnoreCase("openai-compatible"))
                && StringUtils.hasText(properties.getEmbeddingBaseUrl())
                && StringUtils.hasText(properties.getEmbeddingPath())
                && StringUtils.hasText(properties.getEmbeddingApiKey())
                && StringUtils.hasText(properties.getEmbeddingModel());
    }

    private int configuredDimensions() {
        int value = properties.getEmbeddingDimensions();
        return Math.max(1, Math.min(value, 4096));
    }

    private int timeoutMillis() {
        return Math.max(100, Math.min(properties.getEmbeddingOperationTimeoutMillis(), 10_000));
    }

    private boolean isCoolingDown() {
        return unavailableUntilMillis.get() > System.currentTimeMillis();
    }

    private void openCooldown() {
        long cooldown = Math.max(1, Math.min(properties.getEmbeddingFailureCooldownSeconds(), 600)) * 1000L;
        unavailableUntilMillis.set(System.currentTimeMillis() + cooldown);
    }
}
