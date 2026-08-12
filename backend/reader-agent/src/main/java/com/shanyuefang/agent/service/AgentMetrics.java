package com.shanyuefang.agent.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/** Small, low-cardinality operational signals suitable for Prometheus/Actuator scraping. */
@Service
@RequiredArgsConstructor
public class AgentMetrics {
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    /** Emits a trace span without attaching prompts, keys, or other sensitive request content. */
    public <T> T observeModelCall(String mode, String provider, Supplier<T> action) {
        String safeMode = mode == null ? "unknown" : mode.toLowerCase();
        String safeProvider = provider == null || provider.isBlank() ? "unknown" : provider.toLowerCase();
        return Observation.createNotStarted("reader.agent.model", observationRegistry)
                .lowCardinalityKeyValue("mode", safeMode)
                .lowCardinalityKeyValue("provider", safeProvider)
                .observe(action::get);
    }

    public void recordModelCall(String mode, boolean degraded, long startedAtNanos) {
        String outcome = degraded ? "fallback" : "success";
        Timer.builder("reader.agent.model.call")
                .tag("mode", mode.toLowerCase())
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
        meterRegistry.counter("reader.agent.model.requests", "mode", mode.toLowerCase(), "outcome", outcome).increment();
    }

    public void recordUsage(String mode, String provider, String tokenSource, int inputTokens, int outputTokens, long platformCostMicros) {
        String safeMode = mode == null ? "unknown" : mode.toLowerCase();
        String safeProvider = provider == null || provider.isBlank() ? "unknown" : provider.toLowerCase();
        String safeSource = tokenSource == null ? "estimated" : tokenSource.toLowerCase();
        meterRegistry.counter("reader.agent.tokens", "mode", safeMode, "provider", safeProvider, "direction", "input", "source", safeSource).increment(Math.max(0, inputTokens));
        meterRegistry.counter("reader.agent.tokens", "mode", safeMode, "provider", safeProvider, "direction", "output", "source", safeSource).increment(Math.max(0, outputTokens));
        if (platformCostMicros > 0) meterRegistry.counter("reader.agent.platform.cost.micros", "provider", safeProvider).increment(platformCostMicros);
    }

    /** Separates external precision-ranker availability from normal model-call fallback metrics. */
    public void recordReranker(String outcome) {
        String safeOutcome = switch (outcome == null ? "" : outcome.toLowerCase()) {
            case "success", "fallback", "disabled" -> outcome.toLowerCase();
            default -> "unknown";
        };
        meterRegistry.counter("reader.agent.reranker.requests", "outcome", safeOutcome).increment();
    }

    /** Tracks optional vector recall separately from model fallback so dependency outages are observable. */
    public void recordVectorRecall(String outcome) {
        String safeOutcome = switch (outcome == null ? "" : outcome.toLowerCase()) {
            case "success", "fallback", "disabled" -> outcome.toLowerCase();
            default -> "unknown";
        };
        meterRegistry.counter("reader.agent.vector.recall", "outcome", safeOutcome).increment();
    }

    /** Tracks optional Neo4j LightRAG neighborhood availability independently from vector recall. */
    public void recordGraphRecall(String outcome) {
        String safeOutcome = switch (outcome == null ? "" : outcome.toLowerCase()) {
            case "success", "fallback", "disabled" -> outcome.toLowerCase();
            default -> "unknown";
        };
        meterRegistry.counter("reader.agent.graph.recall", "outcome", safeOutcome).increment();
    }

    /** Tracks semantic embedding provider availability without recording input text or vectors. */
    public void recordEmbedding(String outcome) {
        String safeOutcome = switch (outcome == null ? "" : outcome.toLowerCase()) {
            case "success", "fallback", "disabled" -> outcome.toLowerCase();
            default -> "unknown";
        };
        meterRegistry.counter("reader.agent.embedding.requests", "outcome", safeOutcome).increment();
    }
}
