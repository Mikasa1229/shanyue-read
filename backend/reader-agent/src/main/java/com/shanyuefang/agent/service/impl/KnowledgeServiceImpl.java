package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.dto.IndexChapterDTO;
import com.shanyuefang.agent.domain.entity.KnowledgeChunk;
import com.shanyuefang.agent.domain.entity.KnowledgeClue;
import com.shanyuefang.agent.domain.entity.KnowledgeVectorProfile;
import com.shanyuefang.agent.domain.entity.KnowledgeDocument;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphEdge;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphNode;
import com.shanyuefang.agent.domain.entity.KnowledgeEntityAlias;
import com.shanyuefang.agent.domain.entity.KnowledgeClueGraphLink;
import com.shanyuefang.agent.domain.entity.LightRagCommunity;
import com.shanyuefang.agent.domain.vo.ClueVO;
import com.shanyuefang.agent.domain.vo.CitationVO;
import com.shanyuefang.agent.domain.vo.KnowledgeGraphVO;
import com.shanyuefang.agent.domain.vo.SimilarBookVO;
import com.shanyuefang.agent.domain.vo.ReadingMapVO;
import com.shanyuefang.agent.domain.vo.GraphReviewClaimVO;
import com.shanyuefang.agent.mapper.KnowledgeChunkMapper;
import com.shanyuefang.agent.mapper.KnowledgeClueMapper;
import com.shanyuefang.agent.mapper.KnowledgeVectorProfileMapper;
import com.shanyuefang.agent.mapper.KnowledgeDocumentMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphEdgeMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphNodeMapper;
import com.shanyuefang.agent.mapper.KnowledgeEntityAliasMapper;
import com.shanyuefang.agent.mapper.KnowledgeClueGraphLinkMapper;
import com.shanyuefang.agent.mapper.LightRagCommunityMapper;
import com.shanyuefang.agent.service.EmbeddingService;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.agent.service.GraphKnowledgeStore;
import com.shanyuefang.agent.service.StructuredGraphExtractor;
import com.shanyuefang.agent.service.ProfileVectorService;
import com.shanyuefang.agent.service.LightRagService;
import com.shanyuefang.agent.service.VectorKnowledgeStore;
import com.shanyuefang.agent.service.ElasticsearchKnowledgeStore;
import com.shanyuefang.agent.service.RerankerService;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {
    private static final String RULE_EXTRACTOR_VERSION = "rule-extractor-v1";
    private static final String APPROVED = "APPROVED";
    /** Approximate Chinese-character targets; sentence boundaries take priority over an exact size. */
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 120;
    private static final Pattern PERSON_PATTERN = Pattern.compile("(?<![\\p{IsHan}])([\\p{IsHan}]{2,4})(?:说道|问道|看着|对|与|和|向|听到|走到|来到|笑道|想起)");
    private static final Pattern CLUE_PATTERN = Pattern.compile("[^。！？]{0,80}(似乎|秘密|奇怪|线索|疑惑|真相|隐约|不对劲|伏笔)[^。！？]{0,100}");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?:在|来到|前往|位于)([\\p{IsHan}]{2,8}(?:城|镇|村|山|府|楼|馆|院|谷|岛))");
    private static final Pattern EVENT_PATTERN = Pattern.compile("[^。！？]{0,70}(?:冲突|战斗|相遇|离开|抵达|失踪|发现|决定|约定)[^。！？]{0,90}");
    private static final Pattern CLUE_RESOLUTION_PATTERN = Pattern.compile("(?:真相|揭晓|原来|答案|解开)");

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeClueMapper clueMapper;
    private final KnowledgeVectorProfileMapper vectorProfileMapper;
    private final KnowledgeGraphNodeMapper nodeMapper;
    private final KnowledgeEntityAliasMapper aliasMapper;
    private final KnowledgeClueGraphLinkMapper clueGraphLinkMapper;
    private final LightRagCommunityMapper communityMapper;
    private final KnowledgeGraphEdgeMapper edgeMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final GraphKnowledgeStore graphKnowledgeStore;
    private final StructuredGraphExtractor structuredGraphExtractor;
    private final ProfileVectorService profileVectorService;
    private final LightRagService lightRagService;
    private final VectorKnowledgeStore vectorKnowledgeStore;
    private final ElasticsearchKnowledgeStore elasticsearchKnowledgeStore;
    private final RerankerService rerankerService;
    private final CanonicalBookFeignClient canonicalBookClient;
    private final AgentProperties agentProperties;
    /** Per-rebuild caches remove the N+1 graph lookups without changing normal indexing semantics. */
    private final ThreadLocal<RebuildContext> rebuildContext = new ThreadLocal<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void indexChapter(IndexChapterDTO dto) {
        String normalized = dto.getContent().replaceAll("\\r\\n?", "\\n").trim();
        String hash = sha256(normalized);
        String embeddingVersion = agentProperties.getEmbeddingModelVersion();
        KnowledgeDocument existing = documentMapper.selectOne(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getCanonicalBookId, dto.getCanonicalBookId())
                .eq(KnowledgeDocument::getChapterIndex, dto.getChapterIndex())
                .eq(KnowledgeDocument::getContentHash, hash));
        if (existing != null && "READY".equals(existing.getIndexStatus())
                && embeddingVersion.equals(existing.getEmbeddingModelVersion())) return;

        // Remove external projections before replacing a changed chapter, so evidence stores never
        // mix obsolete text with the new source version.
        List<Long> previousChunkIds = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getCanonicalBookId, dto.getCanonicalBookId())
                        .eq(KnowledgeChunk::getChapterIndex, dto.getChapterIndex()))
                .stream().map(KnowledgeChunk::getId).toList();
        vectorKnowledgeStore.deleteChunks(previousChunkIds);
        elasticsearchKnowledgeStore.removeChapter(dto.getCanonicalBookId(), dto.getChapterIndex());
        documentMapper.delete(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getCanonicalBookId, dto.getCanonicalBookId())
                .eq(KnowledgeDocument::getChapterIndex, dto.getChapterIndex()));
        chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, dto.getCanonicalBookId())
                .eq(KnowledgeChunk::getChapterIndex, dto.getChapterIndex()));
        edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, dto.getCanonicalBookId())
                .eq(KnowledgeGraphEdge::getFirstChapter, dto.getChapterIndex()));

        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(SnowflakeIdUtil.next());
        document.setCanonicalBookId(dto.getCanonicalBookId());
        document.setChapterIndex(dto.getChapterIndex());
        document.setContentHash(hash);
        document.setContentVersion(dto.getContentVersion());
        document.setEmbeddingModelVersion(embeddingVersion);
        document.setIndexStatus("INDEXING");
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(document);

        for (String chunkContent : chunks(normalized)) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setId(SnowflakeIdUtil.next());
            chunk.setDocumentId(document.getId());
            chunk.setCanonicalBookId(dto.getCanonicalBookId());
            chunk.setChapterIndex(dto.getChapterIndex());
            chunk.setContent(chunkContent);
            chunk.setKeywords(String.join(" ", extractKeywords(chunkContent)));
            chunk.setEmbeddingJson(writeVector(embeddingService.embed(chunkContent)));
            chunk.setEmbeddingModelVersion(embeddingVersion);
            chunkMapper.insert(chunk);
            // These are raw, chapter-bounded evidence projections. They never select or expand
            // graph context; LightRAG remains the sole graph-query architecture.
            vectorKnowledgeStore.index(chunk);
            elasticsearchKnowledgeStore.index(chunk);
        }
        if (bulkIndexMode()) {
            document.setIndexStatus("READY");
            document.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(document);
            return;
        }
        extractGraph(dto.getCanonicalBookId(), dto.getChapterIndex(), normalized);
        indexClues(dto.getCanonicalBookId(), dto.getChapterIndex(), normalized);
        refreshProfiles(dto.getCanonicalBookId());
        lightRagService.refreshChapter(dto.getCanonicalBookId(), dto.getChapterIndex());
        document.setIndexStatus("READY");
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    @Override
    public List<String> retrieve(Long canonicalBookId, Integer currentChapter, String question, int limit) {
        return retrieveEvidence(canonicalBookId, currentChapter, question, limit, 0L).stream()
                .map(RerankerService.Candidate::content).toList();
    }

    @Override
    public List<CitationVO> retrieveCitations(Long canonicalBookId, Integer currentChapter, String question, int limit) {
        return retrieveEvidence(canonicalBookId, currentChapter, question, limit, 0L).stream()
                .map(candidate -> toCitation(canonicalBookId, candidate.content())).toList();
    }

    @Override
    public List<String> retrieve(Long canonicalBookId, Integer currentChapter, String question, int limit, long rolloutSubject) {
        return retrieveEvidence(canonicalBookId, currentChapter, question, limit, rolloutSubject).stream()
                .map(RerankerService.Candidate::content).toList();
    }

    @Override
    public List<CitationVO> retrieveCitations(Long canonicalBookId, Integer currentChapter, String question, int limit, long rolloutSubject) {
        return retrieveEvidence(canonicalBookId, currentChapter, question, limit, rolloutSubject).stream()
                .map(candidate -> toCitation(canonicalBookId, candidate.content())).toList();
    }

    /**
     * Retrieves original chapter excerpts only after the request's canonical work and reader
     * boundary are known. This is a multi-source evidence layer, not a second graph architecture:
     * LightRAG supplies local entity/relationship context separately in AgentService.
     */
    private List<RerankerService.Candidate> retrieveEvidence(Long canonicalBookId, Integer currentChapter, String question, int limit, long rolloutSubject) {
        if (canonicalBookId == null || currentChapter == null || !StringUtils.hasText(question)) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 8));
        Map<String, RerankerService.Candidate> candidates = new LinkedHashMap<>();

        vectorKnowledgeStore.search(question, safeLimit).stream()
                .filter(document -> matchesReadableBook(document, canonicalBookId, currentChapter))
                .limit(Math.max(4, safeLimit * 2))
                .forEach(document -> addCandidate(candidates, chapterExcerpt(intMetadata(document, "chapterIndex"), document.getContent()), 0.60D, "MILVUS"));

        elasticsearchKnowledgeStore.search(canonicalBookId, currentChapter, question, safeLimit * 2).forEach(hit ->
                addCandidate(candidates, chapterExcerpt(hit.chapterIndex(), hit.content()), 0.65D, "ELASTICSEARCH"));

        List<Double> queryVector = embeddingService.embed(question);
        List<ScoredChunk> postgres = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                        .le(KnowledgeChunk::getChapterIndex, currentChapter)
                        .last("LIMIT 600"))
                .stream().map(chunk -> new ScoredChunk(chunk, embeddingService.similarity(queryVector, readVector(chunk.getEmbeddingJson()))
                        + 0.35D * lexicalScore(question, chunk.getContent() + " " + safe(chunk.getKeywords()))))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()).limit(Math.max(6, safeLimit * 3)).toList();
        postgres.forEach(item -> addCandidate(candidates, chapterExcerpt(item.chunk().getChapterIndex(), item.chunk().getContent()), item.score(), "POSTGRESQL"));

        List<RerankerService.Candidate> boundedCandidates = new ArrayList<>(candidates.values());
        // Preserve the original interface path for internal jobs/tests that have no user cohort;
        // interactive requests pass a positive user id and participate in Reranker gray rollout.
        return rolloutSubject > 0
                ? rerankerService.rerank(question, boundedCandidates, safeLimit, rolloutSubject)
                : rerankerService.rerank(question, boundedCandidates, safeLimit);
    }

    private void addCandidate(Map<String, RerankerService.Candidate> candidates, String content, double score, String source) {
        if (!StringUtils.hasText(content)) return;
        RerankerService.Candidate existing = candidates.get(content);
        if (existing == null) {
            candidates.put(content, new RerankerService.Candidate(content, score, source));
            return;
        }
        candidates.put(content, new RerankerService.Candidate(content, Math.max(existing.retrievalScore(), score),
                existing.sources().contains(source) ? existing.sources() : existing.sources() + "," + source));
    }

    private CitationVO toCitation(long canonicalBookId, String content) {
        int chapter = 0;
        String excerpt = content == null ? "" : content;
        Matcher matcher = Pattern.compile("^\\[Chapter (\\d+)]\\s*").matcher(excerpt);
        if (matcher.find()) {
            chapter = Math.max(0, Integer.parseInt(matcher.group(1)) - 1);
            excerpt = excerpt.substring(matcher.end());
        }
        return new CitationVO(canonicalBookId, chapter, excerpt(excerpt, 220));
    }

    private String chapterExcerpt(int chapterIndex, String content) {
        return "[Chapter " + (Math.max(0, chapterIndex) + 1) + "] " + excerpt(safe(content), 900);
    }

    private boolean matchesReadableBook(org.springframework.ai.document.Document document, long canonicalBookId, int currentChapter) {
        return longMetadata(document, "canonicalBookId") == canonicalBookId
                && intMetadata(document, "chapterIndex") <= currentChapter;
    }

    private int intMetadata(org.springframework.ai.document.Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return -1; }
    }

    private long longMetadata(org.springframework.ai.document.Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return -1L; }
    }

    private double lexicalScore(String query, String content) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(content)) return 0D;
        String normalized = content.toLowerCase(Locale.ROOT);
        long matches = extractKeywords(query).stream().filter(normalized::contains).count();
        return Math.min(1D, matches / 4D);
    }

    @Override
    public boolean isVisibleCharacter(long canonicalBookId, int currentChapter, String name) {
        if (!StringUtils.hasText(name) || currentChapter < 0) return false;
        String requestedName = name.trim();
        if (nodeMapper.selectCount(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)
                .eq(KnowledgeGraphNode::getNodeType, "CHARACTER")
                .eq(KnowledgeGraphNode::getName, requestedName)
                .le(KnowledgeGraphNode::getFirstChapter, currentChapter)
                .eq(KnowledgeGraphNode::getReviewStatus, APPROVED)
                .ge(KnowledgeGraphNode::getConfidence, agentProperties.getMinGraphConfidence())) > 0) return true;

        // An interview can use a stored alias only when it resolves to exactly one reader-visible,
        // reviewed character. Ambiguous same-name aliases deliberately fail closed so the UI can
        // ask the reader to select a graph node rather than role-play the wrong person.
        List<KnowledgeEntityAlias> aliases = aliasMapper.selectList(Wrappers.<KnowledgeEntityAlias>lambdaQuery()
                .eq(KnowledgeEntityAlias::getCanonicalBookId, canonicalBookId)
                .eq(KnowledgeEntityAlias::getNodeType, "CHARACTER")
                .eq(KnowledgeEntityAlias::getAlias, requestedName)
                .le(KnowledgeEntityAlias::getFirstChapter, currentChapter)
                .last("LIMIT 2"));
        if (aliases == null || aliases.size() != 1) return false;
        KnowledgeGraphNode resolved = nodeMapper.selectById(aliases.get(0).getNodeId());
        return resolved != null && canonicalBookId == resolved.getCanonicalBookId()
                && "CHARACTER".equals(resolved.getNodeType())
                && resolved.getFirstChapter() != null && resolved.getFirstChapter() <= currentChapter
                && APPROVED.equals(resolved.getReviewStatus())
                && resolved.getConfidence() != null && resolved.getConfidence() >= agentProperties.getMinGraphConfidence();
    }

    @Override
    public KnowledgeGraphVO graph(long canonicalBookId, int currentChapter) {
        List<KnowledgeGraphNode> nodes = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)
                .le(KnowledgeGraphNode::getFirstChapter, currentChapter)
                .eq(KnowledgeGraphNode::getReviewStatus, APPROVED)
                .ge(KnowledgeGraphNode::getConfidence, agentProperties.getMinGraphConfidence())
                .orderByAsc(KnowledgeGraphNode::getFirstChapter)
                .last("LIMIT 120"));
        Set<Long> visibleIds = new HashSet<>();
        List<KnowledgeGraphVO.Node> viewNodes = nodes.stream().peek(node -> visibleIds.add(node.getId()))
                .map(node -> new KnowledgeGraphVO.Node(node.getId(), node.getName(), node.getNodeType(),
                        node.getFirstChapter(), node.getEvidence(), node.getConfidence())).toList();
        List<KnowledgeGraphVO.Edge> viewEdges = edgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                        .eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId)
                        .le(KnowledgeGraphEdge::getFirstChapter, currentChapter)
                        .eq(KnowledgeGraphEdge::getReviewStatus, APPROVED)
                        .ge(KnowledgeGraphEdge::getConfidence, agentProperties.getMinGraphConfidence())
                        .orderByAsc(KnowledgeGraphEdge::getFirstChapter)
                        .last("LIMIT 240"))
                .stream().filter(edge -> visibleIds.contains(edge.getSourceNodeId()) && visibleIds.contains(edge.getTargetNodeId()))
                .map(edge -> new KnowledgeGraphVO.Edge(edge.getSourceNodeId(), edge.getTargetNodeId(), edge.getRelation(),
                        edge.getFirstChapter(), edge.getEvidence(), edge.getConfidence())).toList();
        return new KnowledgeGraphVO(viewNodes, viewEdges);
    }

    @Override
    public List<ClueVO> clues(long canonicalBookId, int currentChapter) {
        return clueMapper.selectList(Wrappers.<KnowledgeClue>lambdaQuery()
                        .eq(KnowledgeClue::getCanonicalBookId, canonicalBookId)
                        .le(KnowledgeClue::getChapterIndex, currentChapter)
                        .eq(KnowledgeClue::getReviewStatus, APPROVED)
                        .orderByDesc(KnowledgeClue::getChapterIndex).last("LIMIT 30"))
                .stream().map(clue -> clue.getResolvedChapter() != null && clue.getResolvedChapter() > currentChapter
                        ? new ClueVO(clue.getChapterIndex(), clue.getExcerpt(), clue.getSignal(), "OPEN", null, null)
                        : new ClueVO(clue.getChapterIndex(), clue.getExcerpt(), clue.getSignal(), clue.getStatus(),
                        clue.getResolvedChapter(), clue.getResolutionEvidence())).toList();
    }

    @Override
    public List<String> timeline(long canonicalBookId, int currentChapter) {
        List<LightRagCommunity> chapterSummaries = communityMapper.selectList(Wrappers.<LightRagCommunity>lambdaQuery()
                .eq(LightRagCommunity::getCanonicalBookId, canonicalBookId)
                .eq(LightRagCommunity::getCommunityLevel, "CHAPTER")
                .le(LightRagCommunity::getChapterEnd, currentChapter)
                .isNull(LightRagCommunity::getDeletedAt)
                .orderByAsc(LightRagCommunity::getChapterStart)
                .last("LIMIT 400"));
        if (!chapterSummaries.isEmpty()) {
            return chapterSummaries.stream()
                    .map(summary -> "Chapter " + (summary.getChapterStart() + 1) + ": " + excerpt(summary.getSummary(), 260))
                    .toList();
        }
        // A partially completed first index can legitimately have chunks before its LightRAG cards.
        // Preserve a bounded fallback rather than fabricating a recap while the cards catch up.
        return chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                        .le(KnowledgeChunk::getChapterIndex, currentChapter)
                        .orderByAsc(KnowledgeChunk::getChapterIndex).last("LIMIT 400"))
                .stream().collect(java.util.stream.Collectors.groupingBy(KnowledgeChunk::getChapterIndex))
                .entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> "第" + (entry.getKey() + 1) + "章：" + excerpt(entry.getValue().get(0).getContent(), 110))
                .toList();
    }

    @Override
    public ReadingMapVO readingMap(long canonicalBookId, int currentChapter) {
        List<KnowledgeGraphNode> events = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                        .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)
                        .eq(KnowledgeGraphNode::getNodeType, "EVENT")
                        .le(KnowledgeGraphNode::getFirstChapter, currentChapter)
                        .eq(KnowledgeGraphNode::getReviewStatus, APPROVED)
                        .ge(KnowledgeGraphNode::getConfidence, agentProperties.getMinGraphConfidence())
                        .orderByAsc(KnowledgeGraphNode::getFirstChapter).last("LIMIT 100"));
        Set<Long> eventIds = events.stream().map(KnowledgeGraphNode::getId).collect(java.util.stream.Collectors.toSet());
        List<ReadingMapVO.Event> mapEvents = events.stream().map(event -> new ReadingMapVO.Event(event.getId(), event.getName(),
                isSideBranch(event.getEvidence()) ? "SIDE" : "MAIN", event.getFirstChapter(), event.getEvidence(), event.getConfidence())).toList();
        List<ReadingMapVO.Link> links = edgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                        .eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId)
                        .in(KnowledgeGraphEdge::getRelation, List.of("LEADS_TO", "CAUSES", "ENABLES", "PREVENTS", "RESOLVES"))
                        .le(KnowledgeGraphEdge::getFirstChapter, currentChapter)
                        .eq(KnowledgeGraphEdge::getReviewStatus, APPROVED)
                        .ge(KnowledgeGraphEdge::getConfidence, agentProperties.getMinGraphConfidence()))
                .stream().filter(edge -> eventIds.contains(edge.getSourceNodeId()) && eventIds.contains(edge.getTargetNodeId()))
                .map(edge -> new ReadingMapVO.Link(edge.getSourceNodeId(), edge.getTargetNodeId(), edge.getRelation(),
                        edge.getEvidence(), edge.getConfidence())).toList();
        return new ReadingMapVO(mapEvents, links);
    }

    @Override
    public List<SimilarBookVO> similarBooks(long canonicalBookId, int currentChapter, int limit) {
        List<KnowledgeChunk> source = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                .le(KnowledgeChunk::getChapterIndex, currentChapter).last("LIMIT 80"));
        if (source.isEmpty()) return List.of();
        List<Double> profile = embeddingService.embed(source.stream().map(KnowledgeChunk::getKeywords).reduce("", (a, b) -> a + " " + b));
        Set<String> sourceKeywords = source.stream().flatMap(chunk -> extractKeywords(chunk.getContent()).stream()).collect(java.util.stream.Collectors.toSet());
        Map<Long, SimilarCandidate> scores = new HashMap<>();
        // Book profiles are the primary DNA retrieval source; chapter vectors remain a compatibility fallback.
        for (KnowledgeVectorProfile candidate : vectorProfileMapper.selectList(Wrappers.<KnowledgeVectorProfile>lambdaQuery()
                .eq(KnowledgeVectorProfile::getProfileType, "BOOK").isNull(KnowledgeVectorProfile::getDeletedAt)
                .ne(KnowledgeVectorProfile::getCanonicalBookId, canonicalBookId).last("LIMIT 1000"))) {
            double score = embeddingService.similarity(profile, readVector(candidate.getEmbeddingJson()));
            Set<String> shared = extractKeywords(candidate.getContent()).stream().filter(sourceKeywords::contains)
                    .collect(java.util.stream.Collectors.toSet());
            scores.put(candidate.getCanonicalBookId(), new SimilarCandidate(score, shared));
        }
        for (KnowledgeChunk chunk : chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .ne(KnowledgeChunk::getCanonicalBookId, canonicalBookId).last("LIMIT 2000"))) {
            double score = embeddingService.similarity(profile, readVector(chunk.getEmbeddingJson()));
            Set<String> shared = extractKeywords(chunk.getContent()).stream().filter(sourceKeywords::contains)
                    .collect(java.util.stream.Collectors.toSet());
            SimilarCandidate current = scores.get(chunk.getCanonicalBookId());
            if (current == null || score > current.score()) scores.put(chunk.getCanonicalBookId(), new SimilarCandidate(score, shared));
        }
        return scores.entrySet().stream().sorted(Map.Entry.<Long, SimilarCandidate>comparingByValue(Comparator.comparingDouble(SimilarCandidate::score)).reversed())
                .map(entry -> {
                    List<String> shared = entry.getValue().sharedKeywords().stream().sorted().limit(5).toList();
                    String explanation = shared.isEmpty() ? "Similar narrative language and indexed reading signals."
                            : "Shared indexed signals: " + String.join(", ", shared) + ".";
                    Map<String, Object> detail = canonicalDetail(entry.getKey());
                    // Similarity is useful only when it can lead back to a canonical readable work.
                    if (!hasReadableSource(detail)) return null;
                    return new SimilarBookVO(entry.getKey(), entry.getValue().score(), shared, explanation,
                            value(detail, "title", "Work #" + entry.getKey()), value(detail, "author", null),
                            value(detail, "coverUrl", null), value(detail, "summary", null));
                }).filter(java.util.Objects::nonNull).limit(Math.max(1, Math.min(limit, 12))).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildGraph(long canonicalBookId) {
        // Rebuild from the durable chunk store so a changed extractor never leaves stale graph claims behind.
        edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery().eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId));
        nodeMapper.delete(Wrappers.<KnowledgeGraphNode>lambdaQuery().eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId));
        clueMapper.delete(Wrappers.<KnowledgeClue>lambdaQuery().eq(KnowledgeClue::getCanonicalBookId, canonicalBookId));
        clueGraphLinkMapper.delete(Wrappers.<KnowledgeClueGraphLink>lambdaQuery().eq(KnowledgeClueGraphLink::getCanonicalBookId, canonicalBookId));
        graphKnowledgeStore.deleteBook(canonicalBookId);
        List<Long> chunkIds = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId))
                .stream().map(KnowledgeChunk::getId).toList();
        vectorKnowledgeStore.deleteChunks(chunkIds);
        elasticsearchKnowledgeStore.removeBook(canonicalBookId);
        profileVectorService.deleteBookProfiles(canonicalBookId);
        aliasMapper.delete(Wrappers.<KnowledgeEntityAlias>lambdaQuery().eq(KnowledgeEntityAlias::getCanonicalBookId, canonicalBookId));
        Map<Integer, StringBuilder> chapters = new TreeMap<>();
        for (KnowledgeChunk chunk : chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId).orderByAsc(KnowledgeChunk::getChapterIndex))) {
            chapters.computeIfAbsent(chunk.getChapterIndex(), ignored -> new StringBuilder()).append(chunk.getContent()).append('\n');
        }
        RebuildContext context = new RebuildContext();
        rebuildContext.set(context);
        try {
            chapters.forEach((chapter, content) -> {
                extractGraph(canonicalBookId, chapter, content.toString());
                indexClues(canonicalBookId, chapter, content.toString());
            });
            refreshProfiles(canonicalBookId);
            lightRagService.refresh(canonicalBookId);
            // Graph rebuilding does not change raw chapter text, but it is a safe point to repair
            // optional evidence projections after a vector or keyword service outage.
            chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId))
                    .forEach(chunk -> { vectorKnowledgeStore.index(chunk); elasticsearchKnowledgeStore.index(chunk); });
            // Neo4j is a projection. Send bounded batches only after the relational source is complete;
            // this avoids one network transaction per edge during a full LightRAG rebuild.
            graphKnowledgeStore.upsertNodes(new ArrayList<>(context.nodes.values()));
            graphKnowledgeStore.replaceEdges(new ArrayList<>(context.edges.values()));
        } finally {
            rebuildContext.remove();
        }
    }

    @Override
    public void reprojectEvidence(long canonicalBookId, int maxChunks) {
        int limit = Math.max(1, Math.min(maxChunks, 5000));
        List<KnowledgeChunk> chunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                .orderByAsc(KnowledgeChunk::getChapterIndex).last("LIMIT " + limit));
        chunks.forEach(chunk -> {
            vectorKnowledgeStore.index(chunk);
            elasticsearchKnowledgeStore.index(chunk);
        });
        log.info("Reprojected {} LightRAG evidence chunks for book {}", chunks.size(), canonicalBookId);
    }

    @Override
    public void reprojectGraph(long canonicalBookId) {
        List<KnowledgeGraphNode> nodes = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId));
        List<KnowledgeGraphEdge> edges = edgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId));
        graphKnowledgeStore.deleteBook(canonicalBookId);
        graphKnowledgeStore.upsertNodes(nodes);
        graphKnowledgeStore.replaceEdges(edges);
        log.info("Reprojected LightRAG graph to Neo4j: book={}, nodes={}, edges={}", canonicalBookId, nodes.size(), edges.size());
    }

    @Override
    public List<GraphReviewClaimVO> graphReviewClaims(long canonicalBookId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<GraphReviewClaimVO> result = new ArrayList<>();
        nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                        .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)
                        .ne(KnowledgeGraphNode::getReviewStatus, APPROVED)
                        .orderByDesc(KnowledgeGraphNode::getConfidence).last("LIMIT " + safeLimit))
                .forEach(node -> result.add(new GraphReviewClaimVO("NODE", node.getId(), node.getName() + " (" + node.getNodeType() + ")",
                        node.getFirstChapter(), node.getEvidence(), node.getConfidence(), node.getSourceModelVersion(), node.getReviewStatus())));
        if (result.size() < safeLimit) {
            edgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                            .eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId)
                            .ne(KnowledgeGraphEdge::getReviewStatus, APPROVED)
                            .orderByDesc(KnowledgeGraphEdge::getConfidence).last("LIMIT " + (safeLimit - result.size())))
                    .forEach(edge -> result.add(new GraphReviewClaimVO("EDGE", edge.getId(), edge.getRelation(), edge.getFirstChapter(),
                            edge.getEvidence(), edge.getConfidence(), edge.getSourceModelVersion(), edge.getReviewStatus())));
        }
        if (result.size() < safeLimit) {
            clueMapper.selectList(Wrappers.<KnowledgeClue>lambdaQuery()
                            .eq(KnowledgeClue::getCanonicalBookId, canonicalBookId)
                            .ne(KnowledgeClue::getReviewStatus, APPROVED)
                            .orderByDesc(KnowledgeClue::getChapterIndex).last("LIMIT " + (safeLimit - result.size())))
                    .forEach(clue -> result.add(new GraphReviewClaimVO("CLUE", clue.getId(), clue.getSignal() == null ? "Clue" : clue.getSignal(),
                            clue.getChapterIndex(), clue.getExcerpt(), 0.70D, clue.getSourceModelVersion(), clue.getReviewStatus())));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewGraphClaim(long canonicalBookId, String claimType, long claimId, String reviewStatus) {
        String type = claimType == null ? "" : claimType.trim().toUpperCase(Locale.ROOT);
        String status = reviewStatus == null ? "" : reviewStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVED", "REJECTED", "PENDING").contains(status)) {
            throw new IllegalArgumentException("Unsupported graph review status");
        }
        if ("NODE".equals(type)) {
            KnowledgeGraphNode node = nodeMapper.selectById(claimId);
            if (node == null || node.getCanonicalBookId() != canonicalBookId) throw new IllegalArgumentException("Graph node not found");
            node.setReviewStatus(status); node.setUpdatedAt(LocalDateTime.now()); nodeMapper.updateById(node); graphKnowledgeStore.upsertNode(node);
        } else if ("EDGE".equals(type)) {
            KnowledgeGraphEdge edge = edgeMapper.selectById(claimId);
            if (edge == null || edge.getCanonicalBookId() != canonicalBookId) throw new IllegalArgumentException("Graph edge not found");
            edge.setReviewStatus(status); edge.setUpdatedAt(LocalDateTime.now()); edgeMapper.updateById(edge); graphKnowledgeStore.upsertEdge(edge);
        } else if ("CLUE".equals(type)) {
            KnowledgeClue clue = clueMapper.selectById(claimId);
            if (clue == null || clue.getCanonicalBookId() != canonicalBookId) throw new IllegalArgumentException("Knowledge clue not found");
            clue.setReviewStatus(status); clue.setUpdatedAt(LocalDateTime.now()); clueMapper.updateById(clue);
        } else {
            throw new IllegalArgumentException("Unsupported graph claim type");
        }
        // Review changes alter the reader-visible LightRAG projection without re-extracting source text.
        refreshProfiles(canonicalBookId);
        lightRagService.refresh(canonicalBookId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBookKnowledge(long canonicalBookId) {
        graphKnowledgeStore.deleteBook(canonicalBookId);
        profileVectorService.deleteBookProfiles(canonicalBookId);
        List<Long> chunkIds = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId))
                .stream().map(KnowledgeChunk::getId).toList();
        vectorKnowledgeStore.deleteChunks(chunkIds);
        elasticsearchKnowledgeStore.removeBook(canonicalBookId);
        aliasMapper.delete(Wrappers.<KnowledgeEntityAlias>lambdaQuery().eq(KnowledgeEntityAlias::getCanonicalBookId, canonicalBookId));
        lightRagService.deleteBook(canonicalBookId);
        clueMapper.delete(Wrappers.<KnowledgeClue>lambdaQuery().eq(KnowledgeClue::getCanonicalBookId, canonicalBookId));
        clueGraphLinkMapper.delete(Wrappers.<KnowledgeClueGraphLink>lambdaQuery().eq(KnowledgeClueGraphLink::getCanonicalBookId, canonicalBookId));
        edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery().eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId));
        nodeMapper.delete(Wrappers.<KnowledgeGraphNode>lambdaQuery().eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId));
        chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId));
        documentMapper.delete(Wrappers.<KnowledgeDocument>lambdaQuery().eq(KnowledgeDocument::getCanonicalBookId, canonicalBookId));
    }

    private void extractGraph(long bookId, int chapter, String content) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PERSON_PATTERN.matcher(content);
        while (matcher.find()) names.add(matcher.group(1));
        List<KnowledgeGraphNode> characters = names.stream().map(name -> upsertNode(bookId, chapter, name, "CHARACTER")).toList();
        for (int index = 1; index < characters.size(); index++) upsertEdge(bookId, chapter, characters.get(index - 1), characters.get(index), "CO_OCCURS", content);
        Matcher locationMatcher = LOCATION_PATTERN.matcher(content);
        while (locationMatcher.find()) {
            KnowledgeGraphNode location = upsertNode(bookId, chapter, locationMatcher.group(1), "LOCATION");
            characters.forEach(character -> upsertEdge(bookId, chapter, character, location, "APPEARS_AT", locationMatcher.group()));
        }
        Matcher eventMatcher = EVENT_PATTERN.matcher(content);
        if (eventMatcher.find()) {
            KnowledgeGraphNode event = upsertNode(bookId, chapter, "Chapter " + (chapter + 1) + " event", "EVENT");
            characters.forEach(character -> upsertEdge(bookId, chapter, character, event, "PARTICIPATES", eventMatcher.group()));
            KnowledgeGraphNode previous = nodeMapper.selectOne(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                    .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).eq(KnowledgeGraphNode::getNodeType, "EVENT")
                    .lt(KnowledgeGraphNode::getFirstChapter, chapter).orderByDesc(KnowledgeGraphNode::getFirstChapter).last("LIMIT 1"));
            if (previous != null) upsertEdge(bookId, chapter, previous, event, "LEADS_TO", eventMatcher.group(), 0.62D);
        }
        StructuredGraphExtractor.Extraction extraction = structuredGraphExtractor.extract(content);
        Map<String, KnowledgeGraphNode> extractedNodes = new HashMap<>();
        for (StructuredGraphExtractor.Entity entity : extraction.entities()) {
            KnowledgeGraphNode node = upsertNode(bookId, chapter, entity.name(), entity.type(), entity.identityHint(), entity.evidence(), entity.confidence(), extraction.sourceModelVersion());
            extractedNodes.put(entityLookupKey(entity.name(), entity.identityHint()), node);
            extractedNodes.putIfAbsent(entity.name(), node);
            entity.aliases().forEach(alias -> {
                upsertAlias(bookId, chapter, node, alias, entity.evidence(), entity.confidence());
                extractedNodes.putIfAbsent(alias, node);
            });
        }
        for (StructuredGraphExtractor.Relation relation : extraction.relations()) {
            KnowledgeGraphNode source = extractedNodes.getOrDefault(entityLookupKey(relation.source(), relation.sourceIdentityHint()), extractedNodes.get(relation.source()));
            KnowledgeGraphNode target = extractedNodes.getOrDefault(entityLookupKey(relation.target(), relation.targetIdentityHint()), extractedNodes.get(relation.target()));
            if (source != null && target != null && !source.getId().equals(target.getId())) {
                upsertEdge(bookId, chapter, source, target, relation.type(), relation.evidence(), relation.confidence(), extraction.sourceModelVersion());
            }
        }
    }

    private void indexClues(long bookId, int chapter, String content) {
        Matcher matcher = CLUE_PATTERN.matcher(content);
        while (matcher.find()) {
            String clueExcerpt = matcher.group().trim();
            String hash = sha256(bookId + ":" + chapter + ":" + clueExcerpt);
            if (clueMapper.selectCount(Wrappers.<KnowledgeClue>lambdaQuery()
                    .eq(KnowledgeClue::getCanonicalBookId, bookId).eq(KnowledgeClue::getContentHash, hash)) > 0) continue;
            KnowledgeClue clue = new KnowledgeClue();
            clue.setId(SnowflakeIdUtil.next()); clue.setCanonicalBookId(bookId); clue.setChapterIndex(chapter);
            clue.setSignal(matcher.groupCount() > 0 ? matcher.group(1) : null); clue.setExcerpt(clueExcerpt); clue.setContentHash(hash);
            clue.setStatus("OPEN"); clue.setSourceModelVersion(RULE_EXTRACTOR_VERSION); clue.setReviewStatus(APPROVED);
            clue.setCreatedAt(LocalDateTime.now()); clue.setUpdatedAt(LocalDateTime.now()); clueMapper.insert(clue);
            linkClueToVisibleGraph(bookId, chapter, clue);
        }
        if (!CLUE_RESOLUTION_PATTERN.matcher(content).find()) return;
        for (KnowledgeClue clue : clueMapper.selectList(Wrappers.<KnowledgeClue>lambdaQuery()
                .eq(KnowledgeClue::getCanonicalBookId, bookId).lt(KnowledgeClue::getChapterIndex, chapter)
                .in(KnowledgeClue::getStatus, List.of("OPEN", "PARTIALLY_RESOLVED")))) {
            int matchingTerms = matchingMeaningfulTerms(clue.getExcerpt(), content);
            if (matchingTerms == 0) continue;
            // One shared entity is only a lead; require corroboration before declaring a clue closed.
            clue.setStatus(matchingTerms >= 2 ? "RESOLVED" : "PARTIALLY_RESOLVED");
            clue.setResolvedChapter(chapter); clue.setResolutionEvidence(excerpt(content, 140));
            clue.setUpdatedAt(LocalDateTime.now()); clueMapper.updateById(clue);
        }
    }

    private boolean sharesMeaningfulTerm(String left, String right) {
        return matchingMeaningfulTerms(left, right) > 0;
    }

    private void linkClueToVisibleGraph(long bookId, int chapter, KnowledgeClue clue) {
        for (KnowledgeGraphNode node : nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).le(KnowledgeGraphNode::getFirstChapter, chapter)
                .orderByDesc(KnowledgeGraphNode::getConfidence).last("LIMIT 80"))) {
            if (!StringUtils.hasText(node.getName()) || !clue.getExcerpt().contains(node.getName())) continue;
            KnowledgeClueGraphLink link = new KnowledgeClueGraphLink(); link.setId(SnowflakeIdUtil.next()); link.setCanonicalBookId(bookId);
            link.setClueId(clue.getId()); link.setNodeId(node.getId()); link.setLinkType("MENTIONS");
            link.setConfidence(Math.min(1D, Math.max(0.55D, node.getConfidence()))); link.setEvidence(excerpt(clue.getExcerpt(), 180)); link.setCreatedAt(LocalDateTime.now());
            clueGraphLinkMapper.insert(link);
        }
        // Event links require two shared meaningful terms, so a single name mention cannot be mislabeled as causality.
        for (KnowledgeGraphNode event : nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).eq(KnowledgeGraphNode::getNodeType, "EVENT")
                .le(KnowledgeGraphNode::getFirstChapter, chapter).last("LIMIT 60"))) {
            int sharedTerms = matchingMeaningfulTerms(clue.getExcerpt(), event.getEvidence());
            if (sharedTerms < 2) continue;
            KnowledgeClueGraphLink link = new KnowledgeClueGraphLink(); link.setId(SnowflakeIdUtil.next()); link.setCanonicalBookId(bookId);
            link.setClueId(clue.getId()); link.setNodeId(event.getId()); link.setLinkType("RELATED_EVENT");
            link.setConfidence(Math.min(0.9D, 0.55D + sharedTerms * 0.1D)); link.setEvidence(excerpt(event.getEvidence(), 180)); link.setCreatedAt(LocalDateTime.now());
            clueGraphLinkMapper.insert(link);
        }
    }

    private int matchingMeaningfulTerms(String left, String right) {
        return (int) extractKeywords(left).stream().filter(term -> term.length() >= 2).distinct().filter(right::contains).count();
    }

    private Map<String, Object> canonicalDetail(long canonicalBookId) {
        try {
            com.shanyuefang.common.result.R<Map<String, Object>> response = canonicalBookClient.detail(agentProperties.getInternalToken(), canonicalBookId);
            return response == null || response.getData() == null ? Map.of() : response.getData();
        } catch (Exception ignored) { return Map.of(); }
    }
    private String value(Map<String, Object> source, String key, String fallback) {
        Object value = source.get(key); return value == null ? fallback : String.valueOf(value);
    }
    private boolean hasReadableSource(Map<String, Object> detail) {
        Object sourceId = detail.get("sourceId");
        Object sourceBookUrl = detail.get("sourceBookUrl");
        return sourceId != null && sourceBookUrl != null && !String.valueOf(sourceBookUrl).isBlank();
    }

    private boolean isSideBranch(String evidence) {
        String value = evidence == null ? "" : evidence;
        return value.contains("另一边") || value.contains("与此同时") || value.contains("此外");
    }

    private void refreshProfiles(long canonicalBookId) {
        List<String> keywords = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                        .orderByDesc(KnowledgeChunk::getChapterIndex).last("LIMIT 600"))
                .stream().map(KnowledgeChunk::getKeywords).filter(StringUtils::hasText).toList();
        profileVectorService.refreshBookProfile(canonicalBookId, keywords);
        profileVectorService.refreshGraphProfiles(canonicalBookId, nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)));
    }

    private boolean bulkIndexMode() {
        return "true".equalsIgnoreCase(System.getenv("AGENT_BULK_INDEX_MODE"));
    }

    private String entityLookupKey(String name, String identityHint) {
        return name + "\u0000" + (identityHint == null ? "" : identityHint.trim());
    }

    private String identityKey(String nodeType, String name, String identityHint) {
        String hint = identityHint == null ? "" : identityHint.replaceAll("\\s+", " ").trim();
        return (nodeType + ":" + name.trim() + (hint.isEmpty() ? "" : ":" + hint)).toLowerCase(Locale.ROOT);
    }

    private KnowledgeGraphNode upsertNode(long bookId, int chapter, String name, String nodeType) {
        return upsertNode(bookId, chapter, name, nodeType, "", "Chapter " + (chapter + 1) + " appearance", 0.70D);
    }

    private KnowledgeGraphNode upsertNode(long bookId, int chapter, String name, String nodeType, String evidence, double confidence) {
        return upsertNode(bookId, chapter, name, nodeType, "", evidence, confidence);
    }

    private KnowledgeGraphNode upsertNode(long bookId, int chapter, String name, String nodeType, String identityHint, String evidence, double confidence) {
        return upsertNode(bookId, chapter, name, nodeType, identityHint, evidence, confidence, RULE_EXTRACTOR_VERSION);
    }

    private KnowledgeGraphNode upsertNode(long bookId, int chapter, String name, String nodeType, String identityHint, String evidence, double confidence, String sourceModelVersion) {
        String identityKey = identityKey(nodeType, name, identityHint);
        KnowledgeGraphNode node = nodeMapper.selectOne(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).eq(KnowledgeGraphNode::getIdentityKey, identityKey));
        if (node == null) {
            List<KnowledgeEntityAlias> aliases = aliasMapper.selectList(Wrappers.<KnowledgeEntityAlias>lambdaQuery()
                    .eq(KnowledgeEntityAlias::getCanonicalBookId, bookId).eq(KnowledgeEntityAlias::getAlias, name)
                    .eq(KnowledgeEntityAlias::getNodeType, nodeType).last("LIMIT 2"));
            // An alias can legitimately refer to multiple same-name entities; never choose one arbitrarily.
            if (aliases.size() == 1) node = nodeMapper.selectById(aliases.get(0).getNodeId());
        }
        if (node == null) {
            node = new KnowledgeGraphNode(); node.setId(SnowflakeIdUtil.next()); node.setCanonicalBookId(bookId);
            node.setName(name); node.setNodeType(nodeType); node.setIdentityKey(identityKey); node.setFirstChapter(chapter); node.setLastChapter(chapter);
            node.setEvidence(evidence); node.setConfidence(confidence); node.setSourceModelVersion(sourceModelVersion);
            node.setReviewStatus(initialReviewStatus(sourceModelVersion));
            node.setCreatedAt(LocalDateTime.now()); node.setUpdatedAt(LocalDateTime.now());
            nodeMapper.insertIfAbsent(node);
            node = nodeMapper.selectOne(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                    .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).eq(KnowledgeGraphNode::getIdentityKey, identityKey));
            if (node == null) return null;
            RebuildContext context = rebuildContext.get();
            if (context != null) context.nodes.put(identityKey, node);
            if (context == null) graphKnowledgeStore.upsertNode(node);
        } else if (chapter > node.getLastChapter()) {
            node.setLastChapter(chapter); node.setUpdatedAt(LocalDateTime.now()); nodeMapper.updateById(node);
            if (rebuildContext.get() == null) graphKnowledgeStore.upsertNode(node);
        }
        RebuildContext context = rebuildContext.get();
        if (context != null) context.nodes.put(identityKey, node);
        return node;
    }

    private void upsertAlias(long bookId, int chapter, KnowledgeGraphNode node, String alias, String evidence, double confidence) {
        if (!StringUtils.hasText(alias) || alias.equals(node.getName())) return;
        KnowledgeEntityAlias current = aliasMapper.selectOne(Wrappers.<KnowledgeEntityAlias>lambdaQuery()
                .eq(KnowledgeEntityAlias::getCanonicalBookId, bookId).eq(KnowledgeEntityAlias::getAlias, alias)
                .eq(KnowledgeEntityAlias::getNodeType, node.getNodeType()).eq(KnowledgeEntityAlias::getNodeId, node.getId()).last("LIMIT 1"));
        if (current == null) {
            current = new KnowledgeEntityAlias(); current.setId(SnowflakeIdUtil.next()); current.setCanonicalBookId(bookId);
            current.setNodeId(node.getId()); current.setAlias(alias); current.setNodeType(node.getNodeType()); current.setFirstChapter(chapter);
            current.setEvidence(excerpt(evidence, 180)); current.setConfidence(confidence); current.setCreatedAt(LocalDateTime.now());
        }
        current.setUpdatedAt(LocalDateTime.now());
        if (aliasMapper.selectById(current.getId()) == null) aliasMapper.insertIfAbsent(current); else aliasMapper.updateById(current);
    }

    private void upsertEdge(long bookId, int chapter, KnowledgeGraphNode left, KnowledgeGraphNode right, String relation, String content) {
        upsertEdge(bookId, chapter, left, right, relation, content, 0.70D);
    }

    private void upsertEdge(long bookId, int chapter, KnowledgeGraphNode left, KnowledgeGraphNode right, String relation, String content, double confidence) {
        upsertEdge(bookId, chapter, left, right, relation, content, confidence, RULE_EXTRACTOR_VERSION);
    }

    private void upsertEdge(long bookId, int chapter, KnowledgeGraphNode left, KnowledgeGraphNode right, String relation, String content, double confidence, String sourceModelVersion) {
        String edgeKey = bookId + ":" + left.getId() + ":" + right.getId() + ":" + relation;
        RebuildContext context = rebuildContext.get();
        KnowledgeGraphEdge edge = context == null ? null : context.edges.get(edgeKey);
        if (edge == null) edge = edgeMapper.selectOne(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, bookId).eq(KnowledgeGraphEdge::getSourceNodeId, left.getId())
                .eq(KnowledgeGraphEdge::getTargetNodeId, right.getId()).eq(KnowledgeGraphEdge::getRelation, relation));
        if (edge == null) {
            edge = new KnowledgeGraphEdge(); edge.setId(SnowflakeIdUtil.next()); edge.setCanonicalBookId(bookId);
            edge.setSourceNodeId(left.getId()); edge.setTargetNodeId(right.getId()); edge.setRelation(relation);
            edge.setFirstChapter(chapter); edge.setLastChapter(chapter); edge.setEvidence(excerpt(content, 100)); edge.setConfidence(confidence);
            edge.setSourceModelVersion(sourceModelVersion); edge.setReviewStatus(initialReviewStatus(sourceModelVersion));
            edge.setCreatedAt(LocalDateTime.now()); edge.setUpdatedAt(LocalDateTime.now());
            edgeMapper.insertIfAbsent(edge);
            edge = edgeMapper.selectOne(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                    .eq(KnowledgeGraphEdge::getCanonicalBookId, bookId).eq(KnowledgeGraphEdge::getSourceNodeId, left.getId())
                    .eq(KnowledgeGraphEdge::getTargetNodeId, right.getId()).eq(KnowledgeGraphEdge::getRelation, relation));
            if (edge == null) return;
            if (context == null) graphKnowledgeStore.upsertEdge(edge);
        }
        if (context != null) context.edges.put(edgeKey, edge);
    }

    private static final class RebuildContext {
        private final Map<String, KnowledgeGraphNode> nodes = new ConcurrentHashMap<>();
        private final Map<String, KnowledgeGraphEdge> edges = new ConcurrentHashMap<>();
    }

    static List<String> chunks(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Matcher matcher = Pattern.compile("[^。！？!?\\n]+[。！？!?]?|\\n+").matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (sentence.isEmpty()) continue;
            for (String part : splitLongSentence(sentence)) {
                if (!current.isEmpty() && current.length() + part.length() > CHUNK_SIZE) {
                    result.add(current.toString());
                    current = new StringBuilder(overlap(current.toString()));
                }
                current.append(part);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    private static List<String> splitLongSentence(String sentence) {
        // Leave room for overlap when a sentence starts the next semantic chunk.
        int maximumPartSize = CHUNK_SIZE - CHUNK_OVERLAP;
        if (sentence.length() <= maximumPartSize) return List.of(sentence);
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < sentence.length(); start += maximumPartSize) {
            parts.add(sentence.substring(start, Math.min(sentence.length(), start + maximumPartSize)));
        }
        return parts;
    }

    private static String overlap(String previous) {
        if (previous.length() <= CHUNK_OVERLAP) return previous;
        return previous.substring(previous.length() - CHUNK_OVERLAP);
    }

    /** Rule-derived evidence is deterministic; optional model-derived claims require a human decision. */
    private String initialReviewStatus(String sourceModelVersion) {
        return sourceModelVersion != null && sourceModelVersion.startsWith("llm:") ? "PENDING" : APPROVED;
    }

    private Set<String> extractKeywords(String content) {
        Set<String> words = new HashSet<>();
        Matcher matcher = Pattern.compile("[\\p{IsHan}]{2,6}").matcher(content);
        while (matcher.find() && words.size() < 32) words.add(matcher.group());
        return words;
    }

    private String writeVector(List<Double> vector) {
        try { return objectMapper.writeValueAsString(vector); } catch (Exception e) { throw new IllegalStateException("Could not serialize embedding", e); }
    }

    private List<Double> readVector(String json) {
        try { return objectMapper.readValue(json, new TypeReference<List<Double>>() { }); } catch (Exception e) { return List.of(); }
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(); for (byte item : digest) value.append(String.format(Locale.ROOT, "%02x", item)); return value.toString();
        } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private String excerpt(String content, int length) { return content.length() <= length ? content : content.substring(0, length) + "..."; }
    private String safe(String content) { return content == null ? "" : content; }
    private record ScoredChunk(KnowledgeChunk chunk, double score) { }
    private record SimilarCandidate(double score, Set<String> sharedKeywords) { }
}
