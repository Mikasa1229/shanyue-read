package com.shanyuefang.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.service.AgentMetrics;
import com.shanyuefang.agent.service.EmbeddingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredEmbeddingServiceTest {
    @Test
    void disabledProviderUsesDeterministicFallbackAndExposesConfiguredDimension() {
        AgentProperties properties = new AgentProperties();
        properties.setEmbeddingDimensions(384);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EmbeddingService fallback = new HashEmbeddingService(properties);
        ConfiguredEmbeddingService service = new ConfiguredEmbeddingService(properties, new ObjectMapper(),
                new AgentMetrics(registry, ObservationRegistry.NOOP), fallback);

        assertThat(service.embed("人物关系")).hasSize(384);
        assertThat(service.dimensions()).isEqualTo(384);
        assertThat(registry.get("reader.agent.embedding.requests").tag("outcome", "disabled").counter().count())
                .isEqualTo(1D);
    }

    @Test
    void configuredProviderUsesOpenAiEmbeddingBatchContract() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            requests.incrementAndGet();
            String body = "{\"data\":[{\"index\":1,\"embedding\":[0.4,0.3,0.2,0.1]},{\"index\":0,\"embedding\":[0.1,0.2,0.3,0.4]}]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            AgentProperties properties = new AgentProperties();
            properties.setEmbeddingProvider("openai-compatible");
            properties.setEmbeddingBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setEmbeddingApiKey("embedding-test-key");
            properties.setEmbeddingDimensions(4);
            properties.setEmbeddingModel("fixture-embedding");
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            ConfiguredEmbeddingService service = new ConfiguredEmbeddingService(properties, new ObjectMapper(),
                    new AgentMetrics(registry, ObservationRegistry.NOOP), new HashEmbeddingService(properties));

            assertThat(service.embedAll(List.of("第一段", "第二段"))).containsExactly(
                    List.of(0.1D, 0.2D, 0.3D, 0.4D), List.of(0.4D, 0.3D, 0.2D, 0.1D));
            assertThat(requests).hasValue(1);
            assertThat(registry.get("reader.agent.embedding.requests").tag("outcome", "success").counter().count())
                    .isEqualTo(1D);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invalidProviderResponseFallsBackAndOpensCooldown() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = "{\"data\":[{\"embedding\":[0.1]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            AgentProperties properties = new AgentProperties();
            properties.setEmbeddingProvider("openai");
            properties.setEmbeddingBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setEmbeddingApiKey("embedding-test-key");
            properties.setEmbeddingDimensions(4);
            properties.setEmbeddingFailureCooldownSeconds(2);
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            ConfiguredEmbeddingService service = new ConfiguredEmbeddingService(properties, new ObjectMapper(),
                    new AgentMetrics(registry, ObservationRegistry.NOOP), new HashEmbeddingService(properties));

            assertThat(service.embed("first")).hasSize(4);
            assertThat(service.embed("second")).hasSize(4);
            assertThat(requests).hasValue(1);
            assertThat(registry.get("reader.agent.embedding.requests").tag("outcome", "fallback").counter().count())
                    .isEqualTo(2D);
        } finally {
            server.stop(0);
        }
    }
}
