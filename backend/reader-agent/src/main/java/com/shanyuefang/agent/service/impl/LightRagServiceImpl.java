package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.entity.KnowledgeChunk;
import com.shanyuefang.agent.domain.entity.LightRagCommunity;
import com.shanyuefang.agent.mapper.KnowledgeChunkMapper;
import com.shanyuefang.agent.mapper.LightRagCommunityMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphNodeMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphEdgeMapper;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphNode;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphEdge;
import com.shanyuefang.agent.service.EmbeddingService;
import com.shanyuefang.agent.service.LightRagService;
import com.shanyuefang.agent.service.ProfileVectorService;
import com.shanyuefang.agent.service.GraphKnowledgeStore;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LightRagServiceImpl implements LightRagService {
    private static final int ARC_SIZE = 12;
    private static final int LOCAL_GRAPH_EDGE_BUDGET = 36;
    private static final String APPROVED = "APPROVED";
    private final LightRagCommunityMapper communityMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeGraphNodeMapper graphNodeMapper;
    private final KnowledgeGraphEdgeMapper graphEdgeMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final ProfileVectorService profileVectorService;
    private final AgentProperties properties;
    private final GraphKnowledgeStore graphKnowledgeStore;

    @Override @Transactional(rollbackFor = Exception.class)
    public void refresh(long bookId) {
        List<KnowledgeChunk> chunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, bookId).orderByAsc(KnowledgeChunk::getChapterIndex));
        if (chunks.isEmpty()) return;
        int maxChapter = chunks.get(chunks.size() - 1).getChapterIndex();
        // Keep a durable, evidence-bearing chapter card.  Besides making the compact recap safer,
        // this prevents a reader-facing recap from having to select a raw chunk as its summary.
        Map<Integer, List<KnowledgeChunk>> byChapter = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            byChapter.computeIfAbsent(chunk.getChapterIndex(), ignored -> new java.util.ArrayList<>()).add(chunk);
        }
        for (Map.Entry<Integer, List<KnowledgeChunk>> chapter : byChapter.entrySet()) {
            int index = chapter.getKey();
            upsert(bookId, "CHAPTER", index, index, summarize(chapter.getValue()),
                    entities(bookId, index, index), "chapter-summary");
        }
        for (int start = 0; start <= maxChapter; start += ARC_SIZE) {
            int end = Math.min(maxChapter, start + ARC_SIZE - 1);
            int arcStart = start;
            String text = summarize(chunks.stream().filter(chunk -> chunk.getChapterIndex() >= arcStart && chunk.getChapterIndex() <= end).toList());
            upsert(bookId, "ARC", arcStart, end, text, entities(bookId, arcStart, end), "chapter-window");
        }
        // Do not materialize whole-book QA cards. Book-level recommendation profiles live in
        // ProfileVectorService; the LightRAG answer path stays chapter/arc/local-graph bounded.
        communityMapper.delete(Wrappers.<LightRagCommunity>lambdaQuery().eq(LightRagCommunity::getCanonicalBookId, bookId)
                .in(LightRagCommunity::getCommunityLevel, Set.of("BOOK", "BOOK_SAFE")));
        refreshGraphCommunities(bookId);
        profileVectorService.refreshCommunityProfiles(bookId, communityMapper.selectList(Wrappers.<LightRagCommunity>lambdaQuery()
                .eq(LightRagCommunity::getCanonicalBookId, bookId).isNull(LightRagCommunity::getDeletedAt)));
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void refreshChapter(long bookId, int chapterIndex) {
        List<KnowledgeChunk> chapterChunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, bookId)
                .eq(KnowledgeChunk::getChapterIndex, chapterIndex));
        if (chapterChunks.isEmpty()) return;
        upsert(bookId, "CHAPTER", chapterIndex, chapterIndex, summarize(chapterChunks),
                entities(bookId, chapterIndex, chapterIndex), "chapter-summary");

        int arcStart = (chapterIndex / ARC_SIZE) * ARC_SIZE;
        int arcEnd = chapterIndex;
        List<KnowledgeChunk> arcChunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, bookId)
                .ge(KnowledgeChunk::getChapterIndex, arcStart)
                .le(KnowledgeChunk::getChapterIndex, arcEnd)
                .orderByAsc(KnowledgeChunk::getChapterIndex));
        upsert(bookId, "ARC", arcStart, arcEnd, summarize(arcChunks),
                entities(bookId, arcStart, arcEnd), "chapter-window");
        // Graph communities are rebuilt explicitly after bulk imports or graph reviews.
    }

    @Override
    public List<String> context(long bookId, int currentChapter, String query, int maxItems, int maxChars) {
        return query(bookId, currentChapter, query, maxItems, maxChars).communities();
    }

    @Override
    public LightRagQuery query(long bookId, int currentChapter, String query, int maxCommunityItems, int maxCommunityChars) {
        List<String> seeds = resolveSeeds(bookId, currentChapter, query);
        List<String> localEdges = graphKnowledgeStore.localNeighborhood(bookId, currentChapter, seeds, LOCAL_GRAPH_EDGE_BUDGET);
        List<LightRagCard> localCards = rankedCards(bookId, currentChapter, query, Set.of("CHAPTER", "GRAPH"), maxCommunityItems, maxCommunityChars);

        // LightRAG expands from entity-grounded local evidence first. Only an empty local result
        // permits broader arc summaries; BOOK and BOOK_SAFE are catalogue/index artifacts, never QA context.
        if (!localEdges.isEmpty() || !localCards.isEmpty()) {
            return new LightRagQuery(localEdges, localCards, false);
        }
        List<LightRagCard> arcCards = rankedCards(bookId, currentChapter, query, Set.of("ARC"), maxCommunityItems, maxCommunityChars);
        return new LightRagQuery(localEdges, arcCards, !arcCards.isEmpty());
    }

    private List<LightRagCard> rankedCards(long bookId, int currentChapter, String query, Set<String> allowedLevels,
                                           int maxItems, int maxChars) {
        List<Double> queryVector = embeddingService.embed(query);
        int[] budget = {0};
        return communityMapper.selectList(Wrappers.<LightRagCommunity>lambdaQuery().eq(LightRagCommunity::getCanonicalBookId, bookId)
                        .le(LightRagCommunity::getChapterEnd, currentChapter).in(LightRagCommunity::getCommunityLevel, allowedLevels)
                        .isNull(LightRagCommunity::getDeletedAt))
                .stream().filter(value -> allowedLevels.contains(value.getCommunityLevel()))
                .sorted(Comparator.comparingDouble((LightRagCommunity value) -> embeddingService.similarity(queryVector, read(value.getEmbeddingJson()))).reversed())
                .filter(value -> {
                    int length = value.getSummary().length() + safe(value.getEntitySummary()).length() + 12;
                    if (budget[0] + length > maxChars) return false;
                    budget[0] += length;
                    return true;
                })
                .limit(Math.max(1, maxItems)).map(value -> new LightRagCard(value.getCommunityLevel(), value.getChapterStart(),
                        value.getChapterEnd(), value.getSummary(), safe(value.getEntitySummary()))).toList();
    }

    private List<String> resolveSeeds(long bookId, int currentChapter, String query) {
        if (query == null || query.isBlank()) return List.of();
        String normalized = query.toLowerCase(Locale.ROOT);
        return graphNodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                        .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).le(KnowledgeGraphNode::getFirstChapter, currentChapter)
                        .eq(KnowledgeGraphNode::getReviewStatus, APPROVED).ge(KnowledgeGraphNode::getConfidence, properties.getMinGraphConfidence())
                        .orderByDesc(KnowledgeGraphNode::getConfidence).last("LIMIT 64"))
                .stream().map(KnowledgeGraphNode::getName).filter(name -> normalized.contains(name.toLowerCase(Locale.ROOT))).limit(3).toList();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void deleteBook(long bookId) { communityMapper.delete(Wrappers.<LightRagCommunity>lambdaQuery().eq(LightRagCommunity::getCanonicalBookId, bookId)); }

    private void upsert(long bookId, String level, int start, int end, String summary, String entities, String communityKey) {
        String hash = hash(summary + "|" + entities); LightRagCommunity value = communityMapper.selectOne(Wrappers.<LightRagCommunity>lambdaQuery()
                .eq(LightRagCommunity::getCanonicalBookId, bookId).eq(LightRagCommunity::getCommunityLevel, level)
                .eq(LightRagCommunity::getChapterStart, start).eq(LightRagCommunity::getChapterEnd, end)
                .eq(LightRagCommunity::getCommunityKey, communityKey));
        if (value != null && hash.equals(value.getContentHash()) && embeddingVersion().equals(value.getModelVersion())) return;
        if (value == null) { value = new LightRagCommunity(); value.setId(SnowflakeIdUtil.next()); value.setCanonicalBookId(bookId); value.setCommunityLevel(level); value.setChapterStart(start); value.setChapterEnd(end); }
        value.setSummary(summary); value.setEntitySummary(entities); value.setCommunityKey(communityKey); value.setContentHash(hash); value.setEmbeddingJson(write(embeddingService.embed(summary + " " + entities))); value.setModelVersion(embeddingVersion()); value.setIndexedAt(LocalDateTime.now()); value.setDeletedAt(null);
        if (communityMapper.selectById(value.getId()) == null) communityMapper.insert(value); else communityMapper.updateById(value);
    }
    /**
     * Connected components over verified graph edges are a deterministic, low-token community detector.
     * The resulting card is still bounded and points to evidence-bearing graph entities rather than an LLM summary.
     */
    private void refreshGraphCommunities(long bookId) {
        communityMapper.delete(Wrappers.<LightRagCommunity>lambdaQuery()
                .eq(LightRagCommunity::getCanonicalBookId, bookId).eq(LightRagCommunity::getCommunityLevel, "GRAPH"));
        List<KnowledgeGraphNode> nodes = graphNodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).eq(KnowledgeGraphNode::getReviewStatus, APPROVED)
                .ge(KnowledgeGraphNode::getConfidence, properties.getMinGraphConfidence()).orderByDesc(KnowledgeGraphNode::getConfidence));
        List<KnowledgeGraphEdge> edges = graphEdgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, bookId).eq(KnowledgeGraphEdge::getReviewStatus, APPROVED)
                .ge(KnowledgeGraphEdge::getConfidence, properties.getMinGraphConfidence()).orderByDesc(KnowledgeGraphEdge::getConfidence));
        Map<Long, KnowledgeGraphNode> byId = new HashMap<>();
        Map<Long, Set<Long>> adjacent = new HashMap<>();
        for (KnowledgeGraphNode node : nodes) { byId.put(node.getId(), node); adjacent.put(node.getId(), new HashSet<>()); }
        for (KnowledgeGraphEdge edge : edges) {
            if (!byId.containsKey(edge.getSourceNodeId()) || !byId.containsKey(edge.getTargetNodeId())) continue;
            adjacent.get(edge.getSourceNodeId()).add(edge.getTargetNodeId());
            adjacent.get(edge.getTargetNodeId()).add(edge.getSourceNodeId());
        }
        Set<Long> visited = new HashSet<>();
        for (KnowledgeGraphNode seed : nodes) {
            if (!visited.add(seed.getId())) continue;
            Set<Long> component = new HashSet<>(); component.add(seed.getId());
            ArrayDeque<Long> queue = new ArrayDeque<>(); queue.add(seed.getId());
            while (!queue.isEmpty() && component.size() < 16) {
                Long current = queue.removeFirst();
                for (Long neighbor : adjacent.getOrDefault(current, Set.of())) if (visited.add(neighbor)) { component.add(neighbor); queue.addLast(neighbor); }
            }
            if (component.size() < 2) continue;
            List<KnowledgeGraphNode> members = component.stream().map(byId::get).filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(KnowledgeGraphNode::getName)).toList();
            int start = members.stream().map(KnowledgeGraphNode::getFirstChapter).filter(java.util.Objects::nonNull).min(Integer::compareTo).orElse(0);
            int end = members.stream().map(KnowledgeGraphNode::getLastChapter).filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(start);
            String entitySummary = members.stream().limit(12).map(node -> node.getName() + "(" + node.getNodeType() + ")")
                    .reduce((left, right) -> left + ", " + right).orElse("none");
            String relations = edges.stream().filter(edge -> component.contains(edge.getSourceNodeId()) && component.contains(edge.getTargetNodeId()))
                    .limit(16).map(KnowledgeGraphEdge::getRelation).distinct().reduce((left, right) -> left + ", " + right).orElse("linked");
            String key = "graph-" + hash(members.stream().map(node -> String.valueOf(node.getId())).reduce((left, right) -> left + ":" + right).orElse("")) .substring(0, 24);
            upsert(bookId, "GRAPH", start, end, "已核验的关系社区，关联类型：" + relations + "。", entitySummary, key);
        }
    }
    private String entities(long bookId, int start, int end) {
        return graphNodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery().eq(KnowledgeGraphNode::getCanonicalBookId, bookId)
                .le(KnowledgeGraphNode::getFirstChapter, end).ge(KnowledgeGraphNode::getLastChapter, start)
                .eq(KnowledgeGraphNode::getReviewStatus, APPROVED).ge(KnowledgeGraphNode::getConfidence, properties.getMinGraphConfidence())
                .orderByDesc(KnowledgeGraphNode::getConfidence).last("LIMIT 16"))
                .stream().map(node -> node.getName() + "（" + nodeTypeLabel(node.getNodeType()) + "）").reduce((left, right) -> left + "、" + right).orElse("无");
    }
    private String safe(String value) { return value == null ? "" : value; }
    /**
     * A deterministic community card avoids a second LLM pass during indexing. It preserves chapter
     * provenance and recurring terms, so the answer path can decide whether to fetch exact evidence.
     */
    private String summarize(List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) return "暂无已建立索引的正文依据。";
        Map<String, Integer> keywords = new LinkedHashMap<>();
        StringBuilder evidence = new StringBuilder();
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getKeywords() != null) {
                for (String keyword : chunk.getKeywords().split("[,\\s]+")) {
                    String normalized = keyword.trim().toLowerCase(Locale.ROOT);
                    if (normalized.length() >= 2) keywords.merge(normalized, 1, Integer::sum);
                }
            }
            if (evidence.length() >= 840 || chunk.getContent() == null) continue;
            String excerpt = chunk.getContent().replaceAll("\\s+", " ").trim();
            if (excerpt.isBlank()) continue;
            int remaining = 840 - evidence.length();
            int limit = Math.min(Math.min(160, excerpt.length()), remaining);
            evidence.append(" 第").append(chunk.getChapterIndex() + 1).append("章：")
                    .append(excerpt, 0, Math.max(0, limit)).append(";");
        }
        String terms = keywords.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(12).map(Map.Entry::getKey).reduce((left, right) -> left + "、" + right).orElse("无");
        int first = chunks.get(0).getChapterIndex() + 1;
        int last = chunks.get(chunks.size() - 1).getChapterIndex() + 1;
        String result = "第" + first + "至" + last + "章的内容卡片。重复出现的词：" + terms + "。原文依据：" + evidence;
        return result.substring(0, Math.min(1200, result.length()));
    }
    private String nodeTypeLabel(String type) {
        return switch (type == null ? "" : type) {
            case "CHARACTER" -> "人物";
            case "LOCATION" -> "地点";
            case "ORGANIZATION" -> "组织";
            case "EVENT" -> "事件";
            case "CLUE" -> "线索";
            default -> "实体";
        };
    }
    private String write(List<Double> vector) { try { return objectMapper.writeValueAsString(vector); } catch (Exception e) { throw new IllegalStateException(e); } }
    private List<Double> read(String value) { try { return objectMapper.readValue(value, new com.fasterxml.jackson.core.type.TypeReference<List<Double>>() { }); } catch (Exception e) { return List.of(); } }
    private String hash(String text) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); StringBuilder value = new StringBuilder(); for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item)); return value.toString(); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String embeddingVersion() { return properties.getEmbeddingModelVersion() == null || properties.getEmbeddingModelVersion().isBlank() ? "unknown" : properties.getEmbeddingModelVersion().trim(); }
}
