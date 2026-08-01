package com.shanyuefang.agent.service;

import com.shanyuefang.agent.domain.entity.KnowledgeChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.MilvusVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import com.shanyuefang.agent.config.AgentProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/** Vector evidence recall used after LightRAG selects the book/read boundary. */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorKnowledgeStore {
    private static final ExecutorService OPTIONAL_IO_EXECUTOR = new ThreadPoolExecutor(0, 4, 30L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), runnable -> {
        Thread thread = new Thread(runnable, "agent-milvus-optional-io");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    private final ObjectProvider<MilvusVectorStore> vectorStoreProvider;
    private final AgentProperties properties;
    private final AgentMetrics agentMetrics;
    /** A failed optional dependency is skipped during the cooldown instead of timing out every request. */
    private final AtomicLong unavailableUntilMillis = new AtomicLong(0L);

    public void index(KnowledgeChunk chunk) {
        if (!enabled()) return;
        MilvusVectorStore store = vectorStoreProvider.getIfAvailable();
        if (store == null) return;
        try {
            Document document = new Document(String.valueOf(chunk.getId()), chunk.getContent(), Map.of(
                    "canonicalBookId", chunk.getCanonicalBookId(), "chapterIndex", chunk.getChapterIndex(), "chunkId", chunk.getId()));
            document.setEmbedding(readVector(chunk.getEmbeddingJson()));
            runBounded(() -> { store.add(List.of(document)); return null; });
        } catch (Exception exception) {
            log.warn("LightRAG evidence-vector projection failed: chunkId={}", chunk.getId(), exception);
        }
    }

    public List<Document> search(String query, int limit) {
        if (!enabled()) { agentMetrics.recordVectorRecall("disabled"); return List.of(); }
        if (isCoolingDown()) { agentMetrics.recordVectorRecall("fallback"); return List.of(); }
        MilvusVectorStore store = vectorStoreProvider.getIfAvailable();
        if (store == null) { agentMetrics.recordVectorRecall("fallback"); return List.of(); }
        try {
            List<Document> result = runBounded(() -> store.similaritySearch(
                    SearchRequest.query(query).withTopK(Math.max(8, limit * 4)).withSimilarityThresholdAll()));
            unavailableUntilMillis.set(0L);
            agentMetrics.recordVectorRecall("success");
            return result == null ? List.of() : result;
        } catch (Exception exception) {
            openCooldown(exception);
            agentMetrics.recordVectorRecall("fallback");
            return List.of();
        }
    }

    public void deleteChunks(List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) return;
        if (!enabled()) return;
        MilvusVectorStore store = vectorStoreProvider.getIfAvailable();
        if (store == null) return;
        try { runBounded(() -> { store.delete(chunkIds.stream().map(String::valueOf).toList()); return null; }); }
        catch (Exception exception) { log.warn("LightRAG evidence-vector cleanup failed", exception); }
    }

    private <T> T runBounded(Callable<T> operation) throws Exception {
        Future<T> future = OPTIONAL_IO_EXECUTOR.submit(operation);
        try {
            return future.get(timeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw exception;
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception nested) throw nested;
            throw new IllegalStateException(cause);
        }
    }

    private int timeoutMillis() {
        return Math.max(100, Math.min(properties.getMilvusOperationTimeoutMillis(), 10_000));
    }

    private boolean isCoolingDown() {
        return unavailableUntilMillis.get() > System.currentTimeMillis();
    }

    private void openCooldown(Exception exception) {
        long cooldown = Math.max(1, Math.min(properties.getMilvusFailureCooldownSeconds(), 600)) * 1000L;
        unavailableUntilMillis.set(System.currentTimeMillis() + cooldown);
        log.warn("LightRAG evidence-vector recall degraded; PostgreSQL/Elasticsearch fallback is active for {}s ({})",
                cooldown / 1000L, exception.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private List<Double> readVector(String value) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value, List.class); }
        catch (Exception exception) { return List.of(); }
    }

    private boolean enabled() {
        // An operator can temporarily pause this optional projection during bulk imports
        // without changing the durable application configuration or LightRAG indexing.
        return properties.isMilvusEnabled()
                && !"false".equalsIgnoreCase(System.getenv("AGENT_MILVUS_ENABLED"));
    }
}
