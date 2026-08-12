package com.shanyuefang.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.service.AgentMetrics;
import com.shanyuefang.agent.service.ModelRouteService;
import com.shanyuefang.agent.service.RerankerService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfiguredRerankerServiceTest {
    @Test
    void disabledProviderUsesDeterministicTermAwareFallback() {
        AgentProperties properties = new AgentProperties();
        ConfiguredRerankerService service = service(properties);
        List<RerankerService.Candidate> result = service.rerank("林黛玉", List.of(
                new RerankerService.Candidate("一场无关的宴会", 0.1D, "POSTGRESQL"),
                new RerankerService.Candidate("林黛玉走进庭院", 0.2D, "MILVUS")), 1);
        assertThat(result).extracting(RerankerService.Candidate::content).containsExactly("林黛玉走进庭院");
    }

    @Test
    void malformedExternalResponseFallsBackWithoutLeakingCandidateContentToMetrics() {
        AgentProperties properties = new AgentProperties();
        properties.setRerankerEnabled(true);
        properties.setRerankerBaseUrl("http://127.0.0.1:1");
        properties.setRerankerApiKey("test-key");
        ConfiguredRerankerService service = service(properties);
        List<RerankerService.Candidate> result = service.rerank("线索", List.of(
                new RerankerService.Candidate("隐藏线索出现", 0.1D, "ELASTICSEARCH")), 1);
        assertThat(result).hasSize(1);
    }

    @Test
    void duplicateProviderIndexesAreRejectedInsteadOfReturningDuplicateEvidence() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            byte[] body = "{\"results\":[{\"index\":0},{\"index\":0}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            AgentProperties properties = new AgentProperties();
            properties.setRerankerEnabled(true);
            properties.setRerankerBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setRerankerApiKey("duplicate-key");
            ConfiguredRerankerService service = service(properties);
            assertThat(service.rerank("query", List.of(
                    new RerankerService.Candidate("first", 0.1D, "POSTGRESQL"),
                    new RerankerService.Candidate("second", 0.2D, "MILVUS")), 2))
                    .extracting(RerankerService.Candidate::content).doesNotHaveDuplicates();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void configuredProviderUsesBearerContractAndProviderOrdering() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"results\":[{\"index\":1,\"relevance_score\":0.99},{\"index\":0,\"relevance_score\":0.10}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            AgentProperties properties = new AgentProperties();
            properties.setRerankerEnabled(true);
            properties.setRerankerBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setRerankerApiKey("contract-key");
            properties.setRerankerModel("fixture-reranker");
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            ConfiguredRerankerService service = new ConfiguredRerankerService(properties, new ObjectMapper(),
                    new AgentMetrics(registry, ObservationRegistry.NOOP));
            List<RerankerService.Candidate> result = service.rerank("线索", List.of(
                    new RerankerService.Candidate("第一候选", 0.2D, "POSTGRESQL"),
                    new RerankerService.Candidate("第二候选", 0.1D, "MILVUS")), 2);

            assertThat(result).extracting(RerankerService.Candidate::content)
                    .containsExactly("第二候选", "第一候选");
            assertThat(authorization).hasValue("Bearer contract-key");
            assertThat(requestBody).hasValueSatisfying(body -> {
                assertThat(body).contains("fixture-reranker", "线索", "第一候选", "第二候选", "\"top_n\":2");
            });
            assertThat(registry.get("reader.agent.reranker.requests").tag("outcome", "success").counter().count())
                    .isEqualTo(1D);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void configuredProviderCompletesFiftyRequestFixtureLoad() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = "{\"results\":[{\"index\":0,\"relevance_score\":1.0}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            AgentProperties properties = new AgentProperties();
            properties.setRerankerEnabled(true);
            properties.setRerankerBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setRerankerApiKey("fixture-load-key");
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            ConfiguredRerankerService service = new ConfiguredRerankerService(properties, new ObjectMapper(),
                    new AgentMetrics(registry, ObservationRegistry.NOOP));
            long[] latencies = new long[50];
            for (int index = 0; index < latencies.length; index++) {
                long started = System.nanoTime();
                assertThat(service.rerank("fixture query", List.of(
                        new RerankerService.Candidate("fixture evidence", 0.1D, "POSTGRESQL")), 1)).hasSize(1);
                latencies[index] = (System.nanoTime() - started) / 1_000_000L;
            }
            Arrays.sort(latencies);
            assertThat(requestCount).hasValue(50);
            assertThat(registry.get("reader.agent.reranker.requests").tag("outcome", "success").counter().count())
                    .isEqualTo(50D);
            assertThat(latencies[49]).isLessThan(2000L);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesPersistedRerankerRouteModelForTheStableCohort() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"results\":[{\"index\":0}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            AgentProperties properties = new AgentProperties();
            properties.setRerankerEnabled(true);
            properties.setRerankerBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setRerankerApiKey("route-key");
            properties.setRerankerModel("environment-reranker");
            ModelRouteService routes = mock(ModelRouteService.class);
            when(routes.resolve("RERANKER", 42L, "environment-reranker")).thenReturn("gray-reranker-v2");
            ConfiguredRerankerService service = new ConfiguredRerankerService(properties, new ObjectMapper(),
                    new AgentMetrics(new SimpleMeterRegistry(), ObservationRegistry.NOOP), routes);

            assertThat(service.rerank("fixture", List.of(new RerankerService.Candidate("evidence", 0.1D, "POSTGRESQL")), 1, 42L))
                    .hasSize(1);
            assertThat(requestBody).hasValueSatisfying(body -> assertThat(body).contains("gray-reranker-v2"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void slowProviderOpensCooldownAndFallsBackWithoutRepeatingTheWait() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rerank", exchange -> {
            requestCount.incrementAndGet();
            try { Thread.sleep(1_000L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        server.start();
        try {
            AgentProperties properties = new AgentProperties();
            properties.setRerankerEnabled(true);
            properties.setRerankerBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setRerankerApiKey("slow-provider-key");
            properties.setRerankerOperationTimeoutMillis(100);
            properties.setRerankerFailureCooldownSeconds(2);
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            ConfiguredRerankerService service = new ConfiguredRerankerService(properties, new ObjectMapper(),
                    new AgentMetrics(registry, ObservationRegistry.NOOP));
            List<RerankerService.Candidate> candidates = List.of(new RerankerService.Candidate("evidence", 0.1D, "POSTGRESQL"));

            long started = System.nanoTime();
            assertThat(service.rerank("query", candidates, 1)).hasSize(1);
            assertThat(service.rerank("query", candidates, 1)).hasSize(1);
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

            assertThat(requestCount).hasValue(1);
            assertThat(elapsedMillis).isLessThan(700L);
            assertThat(registry.get("reader.agent.reranker.requests").tag("outcome", "fallback").counter().count())
                    .isEqualTo(2D);
        } finally {
            server.stop(0);
        }
    }

    private ConfiguredRerankerService service(AgentProperties properties) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new ConfiguredRerankerService(properties, new ObjectMapper(), new AgentMetrics(registry, ObservationRegistry.NOOP));
    }
}
