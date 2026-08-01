package com.shanyuefang.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.entity.GraphNeighborhoodCache;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphEdge;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphNode;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.mapper.GraphNeighborhoodCacheMapper;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/** Optional Neo4j projection for graph exploration; relational graph tables remain the fallback source. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphKnowledgeStore {
    private static final ExecutorService OPTIONAL_IO_EXECUTOR = new ThreadPoolExecutor(0, 4, 30L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), runnable -> {
        Thread thread = new Thread(runnable, "agent-neo4j-optional-io");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    private final ObjectProvider<Driver> driverProvider;
    private final GraphNeighborhoodCacheMapper cacheMapper;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final AgentMetrics agentMetrics;
    private final Map<String, CachedNeighborhood> neighborhoodCache = new ConcurrentHashMap<>();
    /** Failed optional graph queries are skipped during the cooldown instead of timing out every request. */
    private final AtomicLong unavailableUntilMillis = new AtomicLong(0L);

    @PostConstruct
    void ensureProjectionIndexes() {
        if (!enabled()) return;
        // Bulk LightRAG projection matches source/target IDs for every edge. Keep this
        // index in the application path so a fresh Neo4j instance does not regress to scans.
        execute("CREATE INDEX novel_entity_id IF NOT EXISTS FOR (n:NovelEntity) ON (n.id)", Map.of());
    }

    public void upsertNode(KnowledgeGraphNode node) {
        invalidateBookCache(node.getCanonicalBookId());
        execute("MERGE (n:NovelEntity {id: $id}) SET n.bookId=$bookId, n.name=$name, n.type=$type, "
                        + "n.firstChapter=$firstChapter, n.lastChapter=$lastChapter, n.evidence=$evidence, n.confidence=$confidence, "
                        + "n.sourceModelVersion=$sourceModelVersion, n.reviewStatus=$reviewStatus",
                Map.ofEntries(Map.entry("id", node.getId()), Map.entry("bookId", node.getCanonicalBookId()),
                        Map.entry("name", node.getName()), Map.entry("type", node.getNodeType()),
                        Map.entry("firstChapter", node.getFirstChapter()), Map.entry("lastChapter", node.getLastChapter()),
                        Map.entry("evidence", node.getEvidence()), Map.entry("confidence", node.getConfidence()),
                        Map.entry("sourceModelVersion", node.getSourceModelVersion()), Map.entry("reviewStatus", node.getReviewStatus())));
    }

    public void upsertEdge(KnowledgeGraphEdge edge) {
        invalidateBookCache(edge.getCanonicalBookId());
        execute("MATCH (from:NovelEntity {id: $source}), (to:NovelEntity {id: $target}) "
                        + "MERGE (from)-[r:CO_OCCURS {id: $id}]->(to) SET r.bookId=$bookId, r.relation=$relation, "
                        + "r.firstChapter=$firstChapter, r.lastChapter=$lastChapter, r.evidence=$evidence, r.confidence=$confidence, "
                        + "r.sourceModelVersion=$sourceModelVersion, r.reviewStatus=$reviewStatus",
                Map.ofEntries(Map.entry("id", edge.getId()), Map.entry("source", edge.getSourceNodeId()),
                        Map.entry("target", edge.getTargetNodeId()), Map.entry("bookId", edge.getCanonicalBookId()),
                        Map.entry("relation", edge.getRelation()), Map.entry("firstChapter", edge.getFirstChapter()),
                        Map.entry("lastChapter", edge.getLastChapter()), Map.entry("evidence", edge.getEvidence()),
                        Map.entry("confidence", edge.getConfidence()), Map.entry("sourceModelVersion", edge.getSourceModelVersion()),
                        Map.entry("reviewStatus", edge.getReviewStatus())));
    }

    /** Project a rebuild in bounded UNWIND batches instead of one Bolt transaction per row. */
    public void upsertNodes(Collection<KnowledgeGraphNode> nodes) {
        if (!enabled() || nodes == null || nodes.isEmpty()) return;
        List<KnowledgeGraphNode> values = new ArrayList<>(nodes);
        for (int start = 0; start < values.size(); start += 500) {
            int end = Math.min(values.size(), start + 500);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (KnowledgeGraphNode node : values.subList(start, end)) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", node.getId()); row.put("bookId", node.getCanonicalBookId()); row.put("name", node.getName());
                row.put("type", node.getNodeType()); row.put("firstChapter", node.getFirstChapter()); row.put("lastChapter", node.getLastChapter());
                row.put("evidence", safe(node.getEvidence())); row.put("confidence", node.getConfidence());
                row.put("sourceModelVersion", safe(node.getSourceModelVersion())); row.put("reviewStatus", safe(node.getReviewStatus()));
                rows.add(row);
            }
            execute("UNWIND $nodes AS item MERGE (n:NovelEntity {id: item.id}) SET n.bookId=item.bookId, n.name=item.name, "
                    + "n.type=item.type, n.firstChapter=item.firstChapter, n.lastChapter=item.lastChapter, n.evidence=item.evidence, "
                    + "n.confidence=item.confidence, n.sourceModelVersion=item.sourceModelVersion, n.reviewStatus=item.reviewStatus",
                    Map.of("nodes", rows));
        }
    }

    public void upsertEdges(Collection<KnowledgeGraphEdge> edges) {
        if (!enabled() || edges == null || edges.isEmpty()) return;
        List<KnowledgeGraphEdge> values = new ArrayList<>(edges);
        for (int start = 0; start < values.size(); start += 500) {
            int end = Math.min(values.size(), start + 500);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (KnowledgeGraphEdge edge : values.subList(start, end)) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", edge.getId()); row.put("bookId", edge.getCanonicalBookId()); row.put("source", edge.getSourceNodeId());
                row.put("target", edge.getTargetNodeId()); row.put("relation", safe(edge.getRelation()));
                row.put("firstChapter", edge.getFirstChapter()); row.put("lastChapter", edge.getLastChapter());
                row.put("evidence", safe(edge.getEvidence())); row.put("confidence", edge.getConfidence());
                row.put("sourceModelVersion", safe(edge.getSourceModelVersion())); row.put("reviewStatus", safe(edge.getReviewStatus()));
                rows.add(row);
            }
            execute("UNWIND $edges AS item MATCH (from:NovelEntity {id: item.source}), (to:NovelEntity {id: item.target}) "
                    + "MERGE (from)-[r:CO_OCCURS {id: item.id}]->(to) SET r.bookId=item.bookId, r.relation=item.relation, "
                    + "r.firstChapter=item.firstChapter, r.lastChapter=item.lastChapter, r.evidence=item.evidence, r.confidence=item.confidence, "
                    + "r.sourceModelVersion=item.sourceModelVersion, r.reviewStatus=item.reviewStatus", Map.of("edges", rows));
        }
    }

    /**
     * Fast path used immediately after deleteBook: there are no relationships to merge,
     * so CREATE plus larger bounded batches avoids an expensive uniqueness lookup per edge.
     */
    public void replaceEdges(Collection<KnowledgeGraphEdge> edges) {
        if (!enabled() || edges == null || edges.isEmpty()) return;
        List<KnowledgeGraphEdge> values = new ArrayList<>(edges);
        for (int start = 0; start < values.size(); start += 5000) {
            int end = Math.min(values.size(), start + 5000);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (KnowledgeGraphEdge edge : values.subList(start, end)) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", edge.getId()); row.put("bookId", edge.getCanonicalBookId()); row.put("source", edge.getSourceNodeId());
                row.put("target", edge.getTargetNodeId()); row.put("relation", safe(edge.getRelation()));
                row.put("firstChapter", edge.getFirstChapter()); row.put("lastChapter", edge.getLastChapter());
                row.put("evidence", safe(edge.getEvidence())); row.put("confidence", edge.getConfidence());
                row.put("sourceModelVersion", safe(edge.getSourceModelVersion())); row.put("reviewStatus", safe(edge.getReviewStatus()));
                rows.add(row);
            }
            execute("UNWIND $edges AS item MATCH (from:NovelEntity {id: item.source}), (to:NovelEntity {id: item.target}) "
                    + "CREATE (from)-[r:CO_OCCURS {id: item.id}]->(to) SET r.bookId=item.bookId, r.relation=item.relation, "
                    + "r.firstChapter=item.firstChapter, r.lastChapter=item.lastChapter, r.evidence=item.evidence, r.confidence=item.confidence, "
                    + "r.sourceModelVersion=item.sourceModelVersion, r.reviewStatus=item.reviewStatus", Map.of("edges", rows));
        }
    }

    public void deleteBook(long canonicalBookId) {
        invalidateBookCache(canonicalBookId);
        execute("MATCH (n:NovelEntity {bookId: $bookId}) DETACH DELETE n", Map.of("bookId", canonicalBookId));
    }

    /**
     * Bounded one/two-hop LightRAG expansion. The graph is never materialized in full and a short-lived
     * cache prevents repeat prompt turns from paying Neo4j traversal cost.
     */
    public List<String> localNeighborhood(long canonicalBookId, int currentChapter, List<String> seedNames, int maxEdges) {
        if (seedNames == null || seedNames.isEmpty() || maxEdges <= 0) return List.of();
        if (!enabled()) { agentMetrics.recordGraphRecall("disabled"); return List.of(); }
        int safeEdges = Math.min(Math.max(1, maxEdges), 36);
        String seedSignature = seedNames.stream().filter(name -> name != null && !name.isBlank())
                .sorted(Comparator.naturalOrder()).reduce((left, right) -> left + "|" + right).orElse("");
        String key = canonicalBookId + ":" + hash(currentChapter + ":" + seedSignature + ":" + safeEdges);
        CachedNeighborhood cached = neighborhoodCache.get(key);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            agentMetrics.recordGraphRecall("success");
            return cached.edges();
        }
        List<String> persistent = loadPersistent(canonicalBookId, key);
        if (persistent != null) {
            neighborhoodCache.put(key, new CachedNeighborhood(persistent, System.currentTimeMillis() + 300_000L));
            agentMetrics.recordGraphRecall("success");
            return persistent;
        }
        if (isCoolingDown()) { agentMetrics.recordGraphRecall("fallback"); return List.of(); }
        Driver driver = driverProvider.getIfAvailable();
        if (driver == null) { agentMetrics.recordGraphRecall("fallback"); return List.of(); }
        try {
            List<String> edges = runBounded(() -> {
                try (Session session = driver.session()) {
                    return session.executeRead(transaction -> transaction.run(
                    "MATCH p=(seed:NovelEntity {bookId: $bookId})-[:CO_OCCURS*1..2]-(neighbor:NovelEntity {bookId: $bookId}) "
                            + "WHERE seed.name IN $seedNames "
                            + "AND all(n IN nodes(p) WHERE n.firstChapter <= $boundary AND n.reviewStatus = 'APPROVED' AND n.confidence >= $minimumConfidence) "
                            + "AND all(r IN relationships(p) WHERE r.firstChapter <= $boundary AND r.reviewStatus = 'APPROVED' AND r.confidence >= $minimumConfidence) "
                            + "UNWIND relationships(p) AS rel WITH DISTINCT rel "
                            + "MATCH (source:NovelEntity {bookId: $bookId})-[rel:CO_OCCURS]-(target:NovelEntity {bookId: $bookId}) "
                            + "RETURN source.name AS source, rel.relation AS relation, target.name AS target, rel.firstChapter AS chapter "
                            + "ORDER BY chapter DESC LIMIT $maxEdges",
                    Map.of("bookId", canonicalBookId, "seedNames", seedNames, "boundary", Math.max(0, currentChapter), "maxEdges", safeEdges,
                            "minimumConfidence", properties.getMinGraphConfidence()))
                    .list(record -> record.get("source").asString() + " -" + record.get("relation").asString() + "-> "
                            + record.get("target").asString() + " (Ch. " + (record.get("chapter").asInt() + 1) + ")"));
                }
            });
            neighborhoodCache.put(key, new CachedNeighborhood(edges, System.currentTimeMillis() + 300_000L));
            storePersistent(canonicalBookId, key, currentChapter, safeEdges, edges);
            unavailableUntilMillis.set(0L);
            agentMetrics.recordGraphRecall("success");
            return edges;
        } catch (Exception exception) {
            openCooldown(exception);
            agentMetrics.recordGraphRecall("fallback");
            log.warn("Neo4j local neighborhood query failed; keeping relational graph active", exception);
            return List.of();
        }
    }

    private List<String> loadPersistent(long bookId, String key) {
        GraphNeighborhoodCache value = cacheMapper.selectOne(Wrappers.<GraphNeighborhoodCache>lambdaQuery()
                .eq(GraphNeighborhoodCache::getCanonicalBookId, bookId)
                .eq(GraphNeighborhoodCache::getCacheKey, key)
                .gt(GraphNeighborhoodCache::getExpiresAt, LocalDateTime.now()));
        if (value == null) return null;
        try {
            return objectMapper.readValue(value.getEdgesJson(), new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            cacheMapper.deleteById(value.getId());
            return null;
        }
    }

    private void storePersistent(long bookId, String key, int currentChapter, int maxEdges, List<String> edges) {
        try {
            GraphNeighborhoodCache value = cacheMapper.selectOne(Wrappers.<GraphNeighborhoodCache>lambdaQuery()
                    .eq(GraphNeighborhoodCache::getCanonicalBookId, bookId).eq(GraphNeighborhoodCache::getCacheKey, key));
            if (value == null) {
                value = new GraphNeighborhoodCache();
                value.setId(SnowflakeIdUtil.next());
                value.setCanonicalBookId(bookId);
                value.setCacheKey(key);
                value.setCreatedAt(LocalDateTime.now());
            }
            value.setCurrentChapter(Math.max(0, currentChapter));
            value.setMaxEdges(maxEdges);
            value.setEdgesJson(objectMapper.writeValueAsString(new ArrayList<>(edges)));
            value.setExpiresAt(LocalDateTime.now().plusMinutes(5));
            value.setUpdatedAt(LocalDateTime.now());
            if (cacheMapper.selectById(value.getId()) == null) cacheMapper.insert(value); else cacheMapper.updateById(value);
        } catch (Exception exception) {
            log.debug("Could not persist graph neighborhood cache", exception);
        }
    }

    private void invalidateBookCache(long bookId) {
        neighborhoodCache.keySet().removeIf(key -> key.startsWith(bookId + ":"));
        try {
            cacheMapper.delete(Wrappers.<GraphNeighborhoodCache>lambdaQuery()
                    .eq(GraphNeighborhoodCache::getCanonicalBookId, bookId));
        } catch (Exception exception) {
            log.debug("Could not invalidate persistent graph neighborhood cache", exception);
        }
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format(Locale.ROOT, "%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void execute(String cypher, Map<String, Object> parameters) {
        if ("false".equalsIgnoreCase(System.getenv("AGENT_NEO4J_ENABLED"))) return;
        Driver driver = driverProvider.getIfAvailable();
        if (driver == null) return;
        try {
            runBounded(() -> {
                try (Session session = driver.session()) {
                    session.executeWriteWithoutResult(transaction -> transaction.run(cypher, parameters));
                    return null;
                }
            });
        } catch (Exception exception) {
            openCooldown(exception);
            log.warn("Neo4j graph sync failed; keeping relational graph active", exception);
        }
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
        return Math.max(100, Math.min(properties.getNeo4jOperationTimeoutMillis(), 10_000));
    }

    private boolean isCoolingDown() {
        return unavailableUntilMillis.get() > System.currentTimeMillis();
    }

    private void openCooldown(Exception exception) {
        long cooldown = Math.max(1, Math.min(properties.getNeo4jFailureCooldownSeconds(), 600)) * 1000L;
        unavailableUntilMillis.set(System.currentTimeMillis() + cooldown);
        log.warn("Neo4j LightRAG query degraded; relational graph fallback is active for {}s ({})",
                cooldown / 1000L, exception.getClass().getSimpleName());
    }

    private boolean enabled() {
        return properties.isNeo4jEnabled() && !"false".equalsIgnoreCase(System.getenv("AGENT_NEO4J_ENABLED"));
    }

    private String safe(String value) { return value == null ? "" : value; }

    private record CachedNeighborhood(List<String> edges, long expiresAt) { }
}
