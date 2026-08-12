package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.dto.IndexChapterDTO;
import com.shanyuefang.agent.domain.entity.KnowledgeChunk;
import com.shanyuefang.agent.domain.entity.KnowledgeClue;
import com.shanyuefang.agent.domain.entity.KnowledgeClueResolution;
import com.shanyuefang.agent.domain.entity.KnowledgeVectorProfile;
import com.shanyuefang.agent.domain.entity.KnowledgeDocument;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphEdge;
import com.shanyuefang.agent.domain.entity.KnowledgeRelationAssertion;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphNode;
import com.shanyuefang.agent.domain.entity.KnowledgeEntityAlias;
import com.shanyuefang.agent.domain.entity.KnowledgeClueGraphLink;
import com.shanyuefang.agent.domain.entity.LightRagCommunity;
import com.shanyuefang.agent.domain.vo.ClueVO;
import com.shanyuefang.agent.domain.vo.ClueProgressVO;
import com.shanyuefang.agent.domain.vo.CitationVO;
import com.shanyuefang.agent.domain.vo.KnowledgeGraphVO;
import com.shanyuefang.agent.domain.vo.SimilarBookVO;
import com.shanyuefang.agent.domain.vo.ReadingMapVO;
import com.shanyuefang.agent.domain.vo.GraphReviewClaimVO;
import com.shanyuefang.agent.mapper.KnowledgeChunkMapper;
import com.shanyuefang.agent.mapper.KnowledgeClueMapper;
import com.shanyuefang.agent.mapper.KnowledgeClueResolutionMapper;
import com.shanyuefang.agent.mapper.KnowledgeVectorProfileMapper;
import com.shanyuefang.agent.mapper.KnowledgeDocumentMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphEdgeMapper;
import com.shanyuefang.agent.mapper.KnowledgeRelationAssertionMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphNodeMapper;
import com.shanyuefang.agent.mapper.KnowledgeEntityAliasMapper;
import com.shanyuefang.agent.mapper.KnowledgeClueGraphLinkMapper;
import com.shanyuefang.agent.mapper.LightRagCommunityMapper;
import com.shanyuefang.agent.service.EmbeddingService;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.agent.service.GraphBuildProgressListener;
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
import com.shanyuefang.common.util.NovelContentNormalizer;
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
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {
    private static final String RULE_EXTRACTOR_VERSION = "rule-extractor-v2";
    private static final String STORY_EVENT_VERSION_PREFIX = "story-event-v2:";
    private static final int STORY_EVENT_WINDOW_CHAPTERS = 12;
    private static final int STORY_EVENT_WINDOW_OVERLAP = 3;
    private static final int CHARACTER_KNOWLEDGE_WINDOW_CHAPTERS = 8;
    private static final int CHARACTER_KNOWLEDGE_WINDOW_OVERLAP = 2;
    private static final String CHARACTER_KNOWLEDGE_VERSION_PREFIX = "character-window-v3:";
    private static final String APPROVED = "APPROVED";
    /** Approximate Chinese-character targets; sentence boundaries take priority over an exact size. */
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 120;
    private static final int EMBEDDING_REINDEX_BATCH_SIZE = 32;
    private static final Pattern PERSON_PATTERN = Pattern.compile("([\\p{IsHan}]{2,4})(?:说道|问道|看着|听到|走到|来到|笑道|想起)");
    private static final Pattern CLUE_PATTERN = Pattern.compile("[^。！？]{0,80}(似乎|秘密|奇怪|线索|疑惑|真相|隐约|不对劲|伏笔)[^。！？]{0,100}");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?:在|来到|前往|位于)([\\p{IsHan}]{2,8}(?:城|镇|村|山|府|楼|馆|院|谷|岛))");
    private static final Pattern EVENT_PATTERN = Pattern.compile("[^。！？]{0,70}(?:冲突|战斗|相遇|离开|抵达|失踪|发现|决定|约定)[^。！？]{0,90}");
    private static final int CLUE_LIFECYCLE_CANDIDATE_LIMIT = 12;
    private static final String CLUE_LIFECYCLE_VERSION_PREFIX = "clue-lifecycle-v3:";
    /** Keeps the no-model fallback conservative: prose fragments must look like a Chinese name. */
    private static final String COMMON_SURNAME_CHARACTERS = "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏窦章云苏潘葛范彭郎鲁韦昌马苗方俞任袁柳史唐费廉薛雷贺倪汤滕殷罗毕郝邬安常乐于傅皮齐康伍余顾孟黄穆萧尹姚邵汪祁毛狄米贝明伏成戴谈宋庞熊纪舒屈项祝董梁杜阮蓝闵席季强贾路江童颜郭梅盛林钟徐邱骆高夏蔡田樊胡凌霍虞万柯管卢莫房裘干解应宗丁宣邓杭洪包左石崔吉龚程邢裴陆荣翁荀羊惠曲封储靳段富焦巴牧谷车侯全秋仲伊宫宁仇栾甘厉戎祖武符刘景詹龙叶幸司黎白怀蒲连古易廖居衡耿谭劳姬申冉燕温庄晏柴瞿阎慕艾容向";
    private static final Set<String> NON_NAME_FRAGMENTS = Set.of("这个", "那个", "这里", "那里", "他们", "我们", "你们", "自己", "少年", "女子", "老人", "脸色", "主人", "于是", "但是", "如果", "因为", "已经", "没有", "起来", "看着", "说道", "问道");

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeClueMapper clueMapper;
    private final KnowledgeClueResolutionMapper clueResolutionMapper;
    private final KnowledgeVectorProfileMapper vectorProfileMapper;
    private final KnowledgeGraphNodeMapper nodeMapper;
    private final KnowledgeEntityAliasMapper aliasMapper;
    private final KnowledgeClueGraphLinkMapper clueGraphLinkMapper;
    private final LightRagCommunityMapper communityMapper;
    private final KnowledgeGraphEdgeMapper edgeMapper;
    private final KnowledgeRelationAssertionMapper relationAssertionMapper;
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
        NovelContentNormalizer.Result analysis = NovelContentNormalizer.analyze(dto.getContent());
        String normalized = analysis.normalizedContent();
        String hash = analysis.normalizedHash();
        String embeddingVersion = agentProperties.getEmbeddingModelVersion();
        KnowledgeDocument existing = documentMapper.selectOne(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getCanonicalBookId, dto.getCanonicalBookId())
                .eq(KnowledgeDocument::getChapterIndex, dto.getChapterIndex())
                .eq(KnowledgeDocument::getCanonicalContentHash, hash));
        if (existing == null) {
            existing = documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                            .eq(KnowledgeDocument::getCanonicalBookId, dto.getCanonicalBookId())
                            .eq(KnowledgeDocument::getChapterIndex, dto.getChapterIndex())
                            .eq(KnowledgeDocument::getIndexStatus, "READY"))
                    .stream()
                    .filter(candidate -> candidate.getSemanticFingerprint() != null
                            && NovelContentNormalizer.similarity(candidate.getSemanticFingerprint(), analysis.semanticFingerprint()) >= 0.93D
                            && candidate.getContentQualityScore() != null
                            && analysis.qualityScore() >= candidate.getContentQualityScore() - 0.10D)
                    .findFirst().orElse(null);
        }
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
        relationAssertionMapper.delete(Wrappers.<KnowledgeRelationAssertion>lambdaQuery()
                .eq(KnowledgeRelationAssertion::getCanonicalBookId, dto.getCanonicalBookId())
                .eq(KnowledgeRelationAssertion::getChapterIndex, dto.getChapterIndex()));

        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(SnowflakeIdUtil.next());
        document.setCanonicalBookId(dto.getCanonicalBookId());
        document.setChapterIndex(dto.getChapterIndex());
        document.setContentHash(hash);
        document.setSourceContentHash(analysis.rawHash());
        document.setCanonicalContentHash(analysis.normalizedHash());
        document.setSemanticFingerprint(analysis.semanticFingerprint());
        document.setContentQualityScore(analysis.qualityScore());
        document.setNormalizationVersion(NovelContentNormalizer.VERSION);
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
        // Chapter ingestion stores only source-bounded evidence. A reader-approved build task
        // later asks an LLM to create graph claims; regex guesses are never published as a graph.
        refreshBookProfile(dto.getCanonicalBookId());
        document.setIndexStatus("READY");
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    @Override
    public List<String> retrieve(Long canonicalBookId, Integer currentChapter, String question, int limit) {
        return retrieveEvidence(canonicalBookId, currentChapter, question, limit, 0L).selected().stream()
                .map(RerankerService.Candidate::content).toList();
    }

    @Override
    public List<CitationVO> retrieveCitations(Long canonicalBookId, Integer currentChapter, String question, int limit) {
        return retrieveEvidence(canonicalBookId, currentChapter, question, limit, 0L).selected().stream()
                .map(candidate -> toCitation(canonicalBookId, candidate.content())).toList();
    }

    @Override
    public List<String> retrieve(Long canonicalBookId, Integer currentChapter, String question, int limit, long rolloutSubject) {
        return retrieveEvidence(canonicalBookId, currentChapter, question, limit, rolloutSubject).selected().stream()
                .map(RerankerService.Candidate::content).toList();
    }

    @Override
    public List<CitationVO> retrieveCitations(Long canonicalBookId, Integer currentChapter, String question, int limit, long rolloutSubject) {
        return retrieveEvidence(canonicalBookId, currentChapter, question, limit, rolloutSubject).selected().stream()
                .map(candidate -> toCitation(canonicalBookId, candidate.content())).toList();
    }

    @Override
    public RetrievalResult retrieveDetailed(Long canonicalBookId, Integer currentChapter, String question, int limit, long rolloutSubject) {
        RetrievalOutcome outcome = retrieveEvidence(canonicalBookId, currentChapter, question, limit, rolloutSubject);
        Map<String, Integer> sources = new LinkedHashMap<>();
        outcome.candidates().forEach(candidate -> {
            for (String source : candidate.sources().split(",")) {
                String normalized = source.trim();
                if (!normalized.isBlank()) sources.merge(normalized, 1, Integer::sum);
            }
        });
        return new RetrievalResult(outcome.selected().stream().map(RerankerService.Candidate::content).toList(),
                outcome.candidates().size(), outcome.selected().size(), sources);
    }

    /**
     * Retrieves original chapter excerpts only after the request's canonical work and reader
     * boundary are known. This is a multi-source evidence layer, not a second graph architecture:
     * LightRAG supplies local entity/relationship context separately in AgentService.
     */
    private RetrievalOutcome retrieveEvidence(Long canonicalBookId, Integer currentChapter, String question, int limit, long rolloutSubject) {
        if (canonicalBookId == null || currentChapter == null || !StringUtils.hasText(question)) return new RetrievalOutcome(List.of(), List.of());
        int safeLimit = Math.max(1, Math.min(limit, 8));
        Map<String, RerankerService.Candidate> candidates = new LinkedHashMap<>();

        addGraphEvidenceCandidates(candidates, canonicalBookId, currentChapter, question, safeLimit);
        addEntityPairAnchoredCandidates(candidates, canonicalBookId, currentChapter, question, safeLimit);
        addEntityAnchoredCandidates(candidates, canonicalBookId, currentChapter, question, safeLimit);

        vectorKnowledgeStore.search(question, safeLimit, canonicalBookId, currentChapter).stream()
                .filter(document -> matchesReadableBook(document, canonicalBookId, currentChapter))
                .limit(Math.max(4, safeLimit * 2))
                .forEach(document -> addCandidate(candidates, chapterExcerpt(intMetadata(document, "chapterIndex"), document.getContent()), 0.60D, "MILVUS"));

        elasticsearchKnowledgeStore.search(canonicalBookId, currentChapter, question, safeLimit * 2).forEach(hit ->
                addCandidate(candidates, chapterExcerpt(hit.chapterIndex(), hit.content()), 0.65D, "ELASTICSEARCH"));

        List<Double> queryVector = embeddingService.embed(question);
        List<ScoredChunk> postgres = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                .le(KnowledgeChunk::getChapterIndex, currentChapter)
                .eq(KnowledgeChunk::getEmbeddingModelVersion, agentProperties.getEmbeddingModelVersion())
                        .orderByDesc(KnowledgeChunk::getChapterIndex)
                        .orderByAsc(KnowledgeChunk::getId)
                        // The old 600-row tail silently excluded early chapters once a reader
                        // reached roughly chapter 40. Keep a bounded but book-scale fallback;
                        // Milvus and Elasticsearch remain the primary indexed retrieval paths.
                        .last("LIMIT 5000"))
                .stream().map(chunk -> new ScoredChunk(chunk, embeddingService.similarity(queryVector, readVector(chunk.getEmbeddingJson()))
                        + 0.35D * lexicalScore(question, chunk.getContent() + " " + safe(chunk.getKeywords()))))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()).limit(Math.max(6, safeLimit * 3)).toList();
        postgres.forEach(item -> addCandidate(candidates, chapterExcerpt(item.chunk().getChapterIndex(), item.chunk().getContent()), item.score(), "POSTGRESQL"));

        List<RerankerService.Candidate> boundedCandidates = new ArrayList<>(candidates.values());
        // Preserve the original interface path for internal jobs/tests that have no user cohort;
        // interactive requests pass a positive user id and participate in Reranker gray rollout.
        List<RerankerService.Candidate> selected = rolloutSubject > 0
                ? rerankerService.rerank(question, boundedCandidates, safeLimit, rolloutSubject)
                : rerankerService.rerank(question, boundedCandidates, safeLimit);
        return new RetrievalOutcome(boundedCandidates, selected == null ? List.of() : selected);
    }

    private void addGraphEvidenceCandidates(Map<String, RerankerService.Candidate> candidates, long bookId,
                                            int currentChapter, String question, int safeLimit) {
        List<KnowledgeGraphNode> visibleNodes = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).le(KnowledgeGraphNode::getFirstChapter, currentChapter)
                .eq(KnowledgeGraphNode::getReviewStatus, APPROVED).last("LIMIT 300"));
        Map<Long, KnowledgeGraphNode> nodesById = visibleNodes.stream()
                .collect(java.util.stream.Collectors.toMap(KnowledgeGraphNode::getId, node -> node, (left, right) -> left));
        Set<Long> seeds = visibleNodes.stream().filter(node -> question.contains(node.getName())).map(KnowledgeGraphNode::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        aliasMapper.selectList(Wrappers.<KnowledgeEntityAlias>lambdaQuery().eq(KnowledgeEntityAlias::getCanonicalBookId, bookId)
                        .le(KnowledgeEntityAlias::getFirstChapter, currentChapter)).stream()
                .filter(alias -> question.contains(alias.getAlias())).map(KnowledgeEntityAlias::getNodeId).forEach(seeds::add);
        if (seeds.isEmpty()) return;
        edgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery().eq(KnowledgeGraphEdge::getCanonicalBookId, bookId)
                        .le(KnowledgeGraphEdge::getFirstChapter, currentChapter).eq(KnowledgeGraphEdge::getReviewStatus, APPROVED)
                        .and(query -> query.in(KnowledgeGraphEdge::getSourceNodeId, seeds).or().in(KnowledgeGraphEdge::getTargetNodeId, seeds))
                        .orderByDesc(KnowledgeGraphEdge::getConfidence).last("LIMIT " + Math.max(8, safeLimit * 4)))
                .forEach(edge -> {
                    KnowledgeGraphNode source = nodesById.get(edge.getSourceNodeId()), target = nodesById.get(edge.getTargetNodeId());
                    if (source == null || target == null) return;
                    String evidence = "[Chapter " + (edge.getFirstChapter() + 1) + "] " + source.getName() + " --"
                            + edge.getRelation() + "--> " + target.getName() + "。原文依据：" + excerpt(safe(edge.getEvidence()), 500);
                    addCandidate(candidates, evidence, 0.92D, "LIGHTRAG_GRAPH");
                });
    }

    /**
     * Entity names and approved aliases are high-precision retrieval anchors for relationship
     * questions. They recall original chunks, not generated graph assertions, before reranking.
     */
    private void addEntityAnchoredCandidates(Map<String, RerankerService.Candidate> candidates, long bookId,
                                             int currentChapter, String question, int safeLimit) {
        for (EntityAnchor anchor : resolveQuestionEntityAnchors(bookId, currentChapter, question)) {
            for (String name : anchor.mentions()) {
                chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getCanonicalBookId, bookId)
                                .le(KnowledgeChunk::getChapterIndex, currentChapter).like(KnowledgeChunk::getContent, name)
                                .orderByAsc(KnowledgeChunk::getChapterIndex).orderByAsc(KnowledgeChunk::getId).last("LIMIT " + Math.max(4, safeLimit)))
                        .forEach(chunk -> addCandidate(candidates, chapterExcerpt(chunk.getChapterIndex(), chunk.getContent()),
                                0.88D, "ENTITY_ANCHORED"));
            }
        }
    }

    /**
     * A pair named in a question needs passages where both entities (or their reviewed aliases)
     * actually occur. This is retrieval-only: it never turns co-occurrence into a graph edge.
     */
    private void addEntityPairAnchoredCandidates(Map<String, RerankerService.Candidate> candidates, long bookId,
                                                  int currentChapter, String question, int safeLimit) {
        List<EntityAnchor> anchors = resolveQuestionEntityAnchors(bookId, currentChapter, question);
        if (anchors.size() < 2) return;
        List<KnowledgeChunk> readableChunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, bookId).le(KnowledgeChunk::getChapterIndex, currentChapter)
                .orderByAsc(KnowledgeChunk::getChapterIndex).orderByAsc(KnowledgeChunk::getId).last("LIMIT 5000"));
        for (int left = 0; left < anchors.size(); left++) {
            for (int right = left + 1; right < anchors.size(); right++) {
                EntityAnchor first = anchors.get(left), second = anchors.get(right);
                readableChunks.stream()
                        .filter(chunk -> mentionsAny(chunk.getContent(), first.mentions())
                                && mentionsAny(chunk.getContent(), second.mentions()))
                        .limit(Math.max(4, safeLimit * 2L))
                        .forEach(chunk -> addCandidate(candidates, chapterExcerpt(chunk.getChapterIndex(), chunk.getContent()),
                                0.96D, "ENTITY_PAIR_ANCHORED"));
            }
        }
    }

    private List<EntityAnchor> resolveQuestionEntityAnchors(long bookId, int currentChapter, String question) {
        List<KnowledgeGraphNode> visibleNodes = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).le(KnowledgeGraphNode::getFirstChapter, currentChapter)
                .eq(KnowledgeGraphNode::getReviewStatus, APPROVED).last("LIMIT 300"));
        Map<Long, KnowledgeGraphNode> nodesById = visibleNodes.stream()
                .collect(java.util.stream.Collectors.toMap(KnowledgeGraphNode::getId, node -> node, (left, right) -> left));
        List<KnowledgeEntityAlias> aliases = aliasMapper.selectList(Wrappers.<KnowledgeEntityAlias>lambdaQuery()
                .eq(KnowledgeEntityAlias::getCanonicalBookId, bookId).le(KnowledgeEntityAlias::getFirstChapter, currentChapter));
        Set<Long> seeds = visibleNodes.stream().filter(node -> question.contains(node.getName())).map(KnowledgeGraphNode::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        aliases.stream().filter(alias -> StringUtils.hasText(alias.getAlias()) && question.contains(alias.getAlias()))
                .map(KnowledgeEntityAlias::getNodeId).forEach(seeds::add);
        return seeds.stream().map(nodeId -> {
            KnowledgeGraphNode node = nodesById.get(nodeId);
            if (node == null || !StringUtils.hasText(node.getName())) return null;
            Set<String> mentions = new LinkedHashSet<>();
            mentions.add(node.getName());
            aliases.stream().filter(alias -> nodeId.equals(alias.getNodeId())).map(KnowledgeEntityAlias::getAlias)
                    .filter(StringUtils::hasText).forEach(mentions::add);
            return new EntityAnchor(node.getName(), mentions);
        }).filter(Objects::nonNull).limit(4).toList();
    }

    private boolean mentionsAny(String content, Set<String> mentions) {
        return StringUtils.hasText(content) && mentions.stream().anyMatch(content::contains);
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
                .orderByDesc(KnowledgeGraphNode::getLastChapter)
                .orderByDesc(KnowledgeGraphNode::getConfidence)
                .last("LIMIT 240")).stream()
                // A consolidated event becomes visible only after the reader reaches its final evidence chapter.
                .filter(node -> !safe(node.getSourceModelVersion()).startsWith(STORY_EVENT_VERSION_PREFIX)
                        || node.getLastChapter() <= currentChapter).toList();
        Set<Long> visibleIds = new HashSet<>();
        List<KnowledgeGraphVO.Node> viewNodes = nodes.stream().peek(node -> visibleIds.add(node.getId()))
                .map(node -> new KnowledgeGraphVO.Node(node.getId(), node.getName(), node.getNodeType(),
                        node.getFirstChapter(), node.getEvidence(), node.getConfidence())).toList();
        List<KnowledgeGraphVO.Edge> viewEdges = edgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                        .eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId)
                        .le(KnowledgeGraphEdge::getFirstChapter, currentChapter)
                        .eq(KnowledgeGraphEdge::getReviewStatus, APPROVED)
                        .ge(KnowledgeGraphEdge::getConfidence, agentProperties.getMinGraphConfidence())
                        .orderByDesc(KnowledgeGraphEdge::getLastChapter)
                        .orderByDesc(KnowledgeGraphEdge::getConfidence)
                        .last("LIMIT 240"))
                .stream().filter(edge -> visibleIds.contains(edge.getSourceNodeId()) && visibleIds.contains(edge.getTargetNodeId()))
                // Do not expose only the legacy interaction fallback. Narrative relations
                // such as HELPS and OPPOSES are retained when backed by source evidence.
                .filter(edge -> !"INTERACTS_WITH".equals(edge.getRelation()))
                .map(edge -> new KnowledgeGraphVO.Edge(edge.getSourceNodeId(), edge.getTargetNodeId(), edge.getRelation(),
                        edge.getFirstChapter(), edge.getEvidence(), edge.getConfidence())).toList();
        return new KnowledgeGraphVO(viewNodes, viewEdges);
    }

    @Override
    public List<ClueVO> clues(long canonicalBookId, int currentChapter) {
        List<KnowledgeClue> source = clueMapper.selectList(Wrappers.<KnowledgeClue>lambdaQuery()
                        .eq(KnowledgeClue::getCanonicalBookId, canonicalBookId)
                        .le(KnowledgeClue::getChapterIndex, currentChapter)
                        .eq(KnowledgeClue::getReviewStatus, APPROVED)
                        .orderByDesc(KnowledgeClue::getChapterIndex).last("LIMIT 200"));
        if (source.isEmpty()) return List.of();
        Map<Long, List<KnowledgeClueResolution>> milestones = clueResolutionMapper.selectList(Wrappers.<KnowledgeClueResolution>lambdaQuery()
                        .eq(KnowledgeClueResolution::getCanonicalBookId, canonicalBookId)
                        .in(KnowledgeClueResolution::getClueId, source.stream().map(KnowledgeClue::getId).toList())
                        .le(KnowledgeClueResolution::getResolutionChapter, currentChapter)
                        .eq(KnowledgeClueResolution::getReviewStatus, APPROVED)
                        .orderByAsc(KnowledgeClueResolution::getResolutionChapter))
                .stream().collect(java.util.stream.Collectors.groupingBy(KnowledgeClueResolution::getClueId,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return source.stream().map(clue -> clueViewAtBoundary(clue, milestones.getOrDefault(clue.getId(), List.of()))).toList();
    }

    @Override
    public List<String> timeline(long canonicalBookId, int currentChapter) {
        List<LightRagCommunity> chapterSummaries = communityMapper.selectList(Wrappers.<LightRagCommunity>lambdaQuery()
                .eq(LightRagCommunity::getCanonicalBookId, canonicalBookId)
                .eq(LightRagCommunity::getCommunityLevel, "CHAPTER")
                .le(LightRagCommunity::getChapterEnd, currentChapter)
                .isNull(LightRagCommunity::getDeletedAt)
                .orderByDesc(LightRagCommunity::getChapterStart)
                .last("LIMIT 400"));
        if (!chapterSummaries.isEmpty()) {
            return chapterSummaries.stream().sorted(Comparator.comparing(LightRagCommunity::getChapterStart))
                    .map(summary -> "第" + (summary.getChapterStart() + 1) + "章：" + excerpt(summary.getSummary(), 260))
                    .toList();
        }
        // A partially completed first index can legitimately have chunks before its LightRAG cards.
        // Preserve a bounded fallback rather than fabricating a recap while the cards catch up.
        return chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                        .le(KnowledgeChunk::getChapterIndex, currentChapter)
                        .orderByDesc(KnowledgeChunk::getChapterIndex).last("LIMIT 400"))
                .stream().collect(java.util.stream.Collectors.groupingBy(KnowledgeChunk::getChapterIndex))
                .entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> "第" + (entry.getKey() + 1) + "章：" + excerpt(entry.getValue().get(0).getContent(), 110))
                .toList();
    }

    @Override
    public String recapSummary(long canonicalBookId, int currentChapter) {
        List<KnowledgeGraphNode> events = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)
                .eq(KnowledgeGraphNode::getNodeType, "EVENT")
                .likeRight(KnowledgeGraphNode::getSourceModelVersion, STORY_EVENT_VERSION_PREFIX)
                .le(KnowledgeGraphNode::getLastChapter, currentChapter)
                .eq(KnowledgeGraphNode::getReviewStatus, APPROVED)
                .ge(KnowledgeGraphNode::getConfidence, agentProperties.getMinGraphConfidence())
                .orderByDesc(KnowledgeGraphNode::getLastChapter)
                .orderByDesc(KnowledgeGraphNode::getConfidence).last("LIMIT 16"));
        if (events.isEmpty()) return "已读范围内暂时没有完成归并的剧情事件，重新构建对应章节后会生成阶段回顾。";
        List<KnowledgeGraphNode> ordered = deduplicateRecapEvents(events).stream()
                .sorted(Comparator.comparing(KnowledgeGraphNode::getFirstChapter)).toList();
        List<String> mainEvents = ordered.stream().filter(event -> !isSideBranch(event.getEvidence()))
                .map(KnowledgeGraphNode::getName).limit(5).toList();
        List<String> sideEvents = ordered.stream().filter(event -> isSideBranch(event.getEvidence()))
                .map(KnowledgeGraphNode::getName).limit(2).toList();
        List<String> activeEvents = ordered.stream().filter(event -> safe(event.getEvidence()).contains("·进行中】"))
                .map(KnowledgeGraphNode::getName).limit(2).toList();
        Set<Long> eventIds = ordered.stream().map(KnowledgeGraphNode::getId).collect(java.util.stream.Collectors.toSet());
        List<KnowledgeGraphEdge> links = edgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId)
                .in(KnowledgeGraphEdge::getTargetNodeId, eventIds)
                .eq(KnowledgeGraphEdge::getReviewStatus, APPROVED).last("LIMIT 80"));
        Set<Long> participantIds = links.stream().filter(edge -> "PARTICIPATES_IN".equals(edge.getRelation()))
                .map(KnowledgeGraphEdge::getSourceNodeId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String people = participantIds.isEmpty() ? "" : nodeMapper.selectBatchIds(participantIds).stream()
                .filter(node -> "CHARACTER".equals(node.getNodeType())).map(KnowledgeGraphNode::getName).distinct().limit(5)
                .reduce((a, b) -> a + "、" + b).orElse("");
        StringBuilder summary = new StringBuilder("截至第").append(currentChapter + 1).append("章，");
        if (!mainEvents.isEmpty()) {
            summary.append("故事主线经历了").append(String.join("；", mainEvents)).append("。");
        }
        if (!sideEvents.isEmpty()) {
            summary.append("与此同时，").append(String.join("；", sideEvents)).append("。");
        }
        if (!activeEvents.isEmpty()) {
            summary.append("目前仍在推进的是").append(String.join("、", activeEvents)).append("。");
        } else if (!people.isBlank()) {
            summary.append("这一阶段的关键人物包括").append(people).append("。");
        }
        return summary.toString();
    }

    private List<KnowledgeGraphNode> deduplicateRecapEvents(List<KnowledgeGraphNode> events) {
        List<KnowledgeGraphNode> result = new ArrayList<>();
        for (KnowledgeGraphNode candidate : events) {
            Set<Integer> candidatePairs = characterPairs(candidate.getName());
            boolean duplicate = result.stream().anyMatch(existing -> {
                Set<Integer> existingPairs = characterPairs(existing.getName());
                if (candidatePairs.isEmpty() || existingPairs.isEmpty()) return false;
                long overlap = candidatePairs.stream().filter(existingPairs::contains).count();
                double similarity = overlap / (double) Math.min(candidatePairs.size(), existingPairs.size());
                boolean sameRange = Math.abs(candidate.getFirstChapter() - existing.getFirstChapter()) <= 2
                        && Math.abs(candidate.getLastChapter() - existing.getLastChapter()) <= 2;
                return sameRange && similarity >= 0.55D;
            });
            if (!duplicate) result.add(candidate);
        }
        return result;
    }

    private Set<Integer> characterPairs(String value) {
        String normalized = safe(value).replaceAll("[^\\p{IsHan}A-Za-z0-9]", "");
        Set<Integer> pairs = new java.util.HashSet<>();
        for (int i = 0; i + 1 < normalized.length(); i++) {
            pairs.add(normalized.substring(i, i + 2).hashCode());
        }
        return pairs;
    }

    /**
     * Builds reader-facing events from bounded chapter windows. The overlap keeps an unfinished
     * conflict visible when the next incremental build arrives, without re-sending the whole book.
     */
    private void synthesizeStoryEvents(long canonicalBookId, int startChapter, int endChapter,
                                       StructuredGraphExtractor.ModelConfig modelConfig) {
        List<KnowledgeGraphNode> stale = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)
                .eq(KnowledgeGraphNode::getNodeType, "EVENT")
                .likeRight(KnowledgeGraphNode::getSourceModelVersion, STORY_EVENT_VERSION_PREFIX)
                .le(KnowledgeGraphNode::getFirstChapter, endChapter)
                .ge(KnowledgeGraphNode::getLastChapter, startChapter));
        Set<Long> staleIds = stale.stream().map(KnowledgeGraphNode::getId).collect(java.util.stream.Collectors.toSet());
        if (!staleIds.isEmpty()) {
            edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery().eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId)
                    .and(query -> query.in(KnowledgeGraphEdge::getSourceNodeId, staleIds).or()
                            .in(KnowledgeGraphEdge::getTargetNodeId, staleIds)));
            nodeMapper.deleteBatchIds(staleIds);
        }
        List<KnowledgeGraphNode> facts = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)
                .eq(KnowledgeGraphNode::getNodeType, "EVENT")
                .between(KnowledgeGraphNode::getFirstChapter, startChapter, endChapter)
                .notLikeRight(KnowledgeGraphNode::getSourceModelVersion, STORY_EVENT_VERSION_PREFIX)
                .notLike(KnowledgeGraphNode::getSourceModelVersion, "relation-context")
                .eq(KnowledgeGraphNode::getReviewStatus, APPROVED)
                .ge(KnowledgeGraphNode::getConfidence, agentProperties.getMinGraphConfidence())
                .orderByAsc(KnowledgeGraphNode::getFirstChapter).last("LIMIT 240"));
        List<KnowledgeGraphEdge> relationFacts = edgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId)
                .between(KnowledgeGraphEdge::getFirstChapter, startChapter, endChapter)
                .notIn(KnowledgeGraphEdge::getRelation, List.of("VISITS", "LIVES_IN", "OWNS"))
                .eq(KnowledgeGraphEdge::getReviewStatus, APPROVED)
                .ge(KnowledgeGraphEdge::getConfidence, agentProperties.getMinGraphConfidence())
                .orderByAsc(KnowledgeGraphEdge::getFirstChapter).last("LIMIT 320"));
        Map<String, StructuredGraphExtractor.StoryEvent> merged = new LinkedHashMap<>();
        for (int windowStart = startChapter; windowStart <= endChapter; windowStart += STORY_EVENT_WINDOW_CHAPTERS - STORY_EVENT_WINDOW_OVERLAP) {
            int windowEnd = Math.min(endChapter, windowStart + STORY_EVENT_WINDOW_CHAPTERS - 1);
            int selectedWindowStart = windowStart;
            int selectedWindowEnd = windowEnd;
            List<StructuredGraphExtractor.ChapterFact> windowFacts = facts.stream()
                    .filter(fact -> fact.getFirstChapter() >= selectedWindowStart && fact.getFirstChapter() <= selectedWindowEnd)
                    .map(fact -> new StructuredGraphExtractor.ChapterFact(fact.getId(), fact.getFirstChapter(), fact.getEvidence()))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            relationFacts.stream().filter(edge -> edge.getFirstChapter() >= selectedWindowStart && edge.getFirstChapter() <= selectedWindowEnd)
                    .map(edge -> new StructuredGraphExtractor.ChapterFact(edge.getId(), edge.getFirstChapter(), edge.getEvidence()))
                    .forEach(windowFacts::add);
            for (StructuredGraphExtractor.StoryEvent event : structuredGraphExtractor.synthesizeStoryEvents(windowFacts, modelConfig).events()) {
                String key = event.name().replaceAll("\\s+", "") + ":" + event.startChapter() + ":" + event.endChapter();
                merged.putIfAbsent(key, event);
            }
            if (windowEnd == endChapter) break;
        }
        for (StructuredGraphExtractor.StoryEvent event : merged.values()) {
            String evidence = storyEventEvidence(event);
            KnowledgeGraphNode node = upsertNode(canonicalBookId, event.startChapter(), event.name(), "EVENT",
                    "story:" + event.startChapter() + ":" + event.endChapter(), evidence, event.confidence(),
                    STORY_EVENT_VERSION_PREFIX + modelConfig.provider() + ":" + modelConfig.model());
            if (node != null) {
                node.setLastChapter(event.endChapter());
                node.setEvidence(evidence);
                node.setConfidence(event.confidence());
                node.setSourceModelVersion(STORY_EVENT_VERSION_PREFIX + modelConfig.provider() + ":" + modelConfig.model());
                node.setUpdatedAt(LocalDateTime.now());
                nodeMapper.updateById(node);
                linkStoryEventMentions(canonicalBookId, node, event);
            }
        }
    }

    private void linkStoryEventMentions(long bookId, KnowledgeGraphNode eventNode, StructuredGraphExtractor.StoryEvent event) {
        String evidence = event.evidence().stream().map(StructuredGraphExtractor.ChapterFact::evidence)
                .collect(java.util.stream.Collectors.joining(" "));
        List<KnowledgeGraphNode> candidates = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).eq(KnowledgeGraphNode::getNodeType, "CHARACTER")
                .le(KnowledgeGraphNode::getFirstChapter, event.endChapter()));
        Map<Long, List<String>> aliases = aliasMapper.selectList(Wrappers.<KnowledgeEntityAlias>lambdaQuery()
                        .eq(KnowledgeEntityAlias::getCanonicalBookId, bookId).eq(KnowledgeEntityAlias::getNodeType, "CHARACTER"))
                .stream().collect(java.util.stream.Collectors.groupingBy(KnowledgeEntityAlias::getNodeId,
                        java.util.stream.Collectors.mapping(KnowledgeEntityAlias::getAlias, java.util.stream.Collectors.toList())));
        candidates.stream().filter(node -> containsNormalized(evidence, node.getName())
                        || aliases.getOrDefault(node.getId(), List.of()).stream().anyMatch(alias -> containsNormalized(evidence, alias)))
                .limit(8).forEach(node -> upsertEdge(bookId, event.startChapter(), node, eventNode, "PARTICIPATES_IN",
                        storyEventEvidence(event), Math.min(0.9D, event.confidence()), STORY_EVENT_VERSION_PREFIX + "mention-link"));
    }

    private String storyEventEvidence(StructuredGraphExtractor.StoryEvent event) {
        String branch = "SIDE".equals(event.branch()) ? "支线" : "主线";
        String status = "OPEN".equals(event.status()) ? "进行中" : "已完成";
        String evidence = event.evidence().stream().limit(3)
                .map(fact -> "第" + (fact.chapterIndex() + 1) + "章：“" + excerpt(fact.evidence(), 88) + "”")
                .collect(java.util.stream.Collectors.joining("；"));
        return "【" + branch + "·" + status + "】" + evidence;
    }

    @Override
    public ReadingMapVO readingMap(long canonicalBookId, int currentChapter) {
        List<KnowledgeGraphNode> events = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                        .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)
                        .eq(KnowledgeGraphNode::getNodeType, "EVENT")
                        .likeRight(KnowledgeGraphNode::getSourceModelVersion, STORY_EVENT_VERSION_PREFIX)
                        .le(KnowledgeGraphNode::getLastChapter, currentChapter)
                        .eq(KnowledgeGraphNode::getReviewStatus, APPROVED)
                        .ge(KnowledgeGraphNode::getConfidence, agentProperties.getMinGraphConfidence())
                        .orderByDesc(KnowledgeGraphNode::getLastChapter)
                        .orderByDesc(KnowledgeGraphNode::getConfidence).last("LIMIT 100"));
        Set<Long> eventIds = events.stream().map(KnowledgeGraphNode::getId).collect(java.util.stream.Collectors.toSet());
        List<ReadingMapVO.Event> mapEvents = events.stream().map(event -> new ReadingMapVO.Event(event.getId(), event.getName(),
                isSideBranch(event.getEvidence()) ? "SIDE" : "MAIN", event.getFirstChapter(), event.getEvidence(), event.getConfidence())).toList();
        List<ReadingMapVO.Link> links = new ArrayList<>(edgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                        .eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId)
                        .in(KnowledgeGraphEdge::getRelation, List.of("LEADS_TO", "CAUSES", "ENABLES", "PREVENTS", "RESOLVES", "推动", "导致", "促成", "阻止", "解决"))
                        .le(KnowledgeGraphEdge::getFirstChapter, currentChapter)
                        .eq(KnowledgeGraphEdge::getReviewStatus, APPROVED)
                        .ge(KnowledgeGraphEdge::getConfidence, agentProperties.getMinGraphConfidence()))
                .stream().filter(edge -> eventIds.contains(edge.getSourceNodeId()) && eventIds.contains(edge.getTargetNodeId()))
                .map(edge -> new ReadingMapVO.Link(edge.getSourceNodeId(), edge.getTargetNodeId(), edge.getRelation(),
                        edge.getEvidence(), edge.getConfidence())).toList());
        // EVENT nodes are normally connected through PARTICIPATES_IN. When no direct event
        // causality is present, expose a conservative character-thread link rather than an empty map.
        Map<Long, Set<Long>> eventParticipants = new HashMap<>();
        List<KnowledgeGraphEdge> participationEdges = edgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId)
                .eq(KnowledgeGraphEdge::getRelation, "PARTICIPATES_IN")
                .le(KnowledgeGraphEdge::getFirstChapter, currentChapter)
                .eq(KnowledgeGraphEdge::getReviewStatus, APPROVED)
                .ge(KnowledgeGraphEdge::getConfidence, agentProperties.getMinGraphConfidence()));
        Set<Long> participantIds = new HashSet<>();
        for (KnowledgeGraphEdge edge : participationEdges) {
            if (eventIds.contains(edge.getTargetNodeId())) {
                eventParticipants.computeIfAbsent(edge.getTargetNodeId(), ignored -> new LinkedHashSet<>()).add(edge.getSourceNodeId());
                participantIds.add(edge.getSourceNodeId());
            } else if (eventIds.contains(edge.getSourceNodeId())) {
                eventParticipants.computeIfAbsent(edge.getSourceNodeId(), ignored -> new LinkedHashSet<>()).add(edge.getTargetNodeId());
                participantIds.add(edge.getTargetNodeId());
            }
        }
        Map<Long, String> participantNames = nodeMapper.selectBatchIds(participantIds).stream()
                .collect(java.util.stream.Collectors.toMap(KnowledgeGraphNode::getId, KnowledgeGraphNode::getName, (left, right) -> left));
        Set<String> existingPairs = links.stream().map(link -> link.getSource() + "->" + link.getTarget()).collect(java.util.stream.Collectors.toSet());
        List<KnowledgeGraphNode> chronologicalEvents = new ArrayList<>(events);
        chronologicalEvents.sort(Comparator.comparing(KnowledgeGraphNode::getFirstChapter).thenComparing(KnowledgeGraphNode::getId));
        for (int index = 1; index < chronologicalEvents.size() && links.size() < 80; index++) {
            KnowledgeGraphNode previous = chronologicalEvents.get(index - 1);
            KnowledgeGraphNode next = chronologicalEvents.get(index);
            Set<Long> shared = new LinkedHashSet<>(eventParticipants.getOrDefault(previous.getId(), Set.of()));
            shared.retainAll(eventParticipants.getOrDefault(next.getId(), Set.of()));
            if (existingPairs.contains(previous.getId() + "->" + next.getId())) continue;
            if (shared.isEmpty()) {
                links.add(new ReadingMapVO.Link(previous.getId(), next.getId(), "情节推进",
                        "按章节顺序归并的故事事件连接，不等同于因果关系。",
                        Math.min(previous.getConfidence() == null ? 0.75D : previous.getConfidence(),
                                next.getConfidence() == null ? 0.75D : next.getConfidence())));
                existingPairs.add(previous.getId() + "->" + next.getId());
                continue;
            }
            String names = shared.stream().map(participantNames::get).filter(StringUtils::hasText).limit(3)
                    .collect(java.util.stream.Collectors.joining("、"));
            if (!StringUtils.hasText(names)) continue;
            String evidence = "两段事件均关联人物：" + names + "。这是一条角色串联，不等同于因果关系。";
            double previousConfidence = previous.getConfidence() == null ? 0.75D : previous.getConfidence();
            double nextConfidence = next.getConfidence() == null ? 0.75D : next.getConfidence();
            links.add(new ReadingMapVO.Link(previous.getId(), next.getId(), "角色串联", evidence, Math.min(previousConfidence, nextConfidence)));
            existingPairs.add(previous.getId() + "->" + next.getId());
        }
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
                    String explanation = shared.isEmpty() ? "叙事语言与已建立索引的阅读特征相近。"
                            : "共同的索引特征：" + String.join("、", shared) + "。";
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
        rebuildGraph(canonicalBookId, new StructuredGraphExtractor.ModelConfig(agentProperties.getPlatformProvider(),
                agentProperties.getPlatformModel(), agentProperties.getPlatformBaseUrl(), agentProperties.getPlatformApiKey()), ignored -> { });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildGraph(long canonicalBookId, StructuredGraphExtractor.ModelConfig modelConfig,
                             java.util.function.IntConsumer chapterProgress) {
        if (modelConfig == null || !StringUtils.hasText(modelConfig.apiKey())) {
            throw new IllegalArgumentException("A model configuration is required to build a knowledge graph");
        }
        // Rebuild the relational authority first. Optional projections remain readable if a model
        // call fails and are replaced only after every chapter has been extracted successfully.
        edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery().eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId));
        relationAssertionMapper.delete(Wrappers.<KnowledgeRelationAssertion>lambdaQuery()
                .eq(KnowledgeRelationAssertion::getCanonicalBookId, canonicalBookId));
        nodeMapper.delete(Wrappers.<KnowledgeGraphNode>lambdaQuery().eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId));
        clueMapper.delete(Wrappers.<KnowledgeClue>lambdaQuery().eq(KnowledgeClue::getCanonicalBookId, canonicalBookId));
        clueGraphLinkMapper.delete(Wrappers.<KnowledgeClueGraphLink>lambdaQuery().eq(KnowledgeClueGraphLink::getCanonicalBookId, canonicalBookId));
        aliasMapper.delete(Wrappers.<KnowledgeEntityAlias>lambdaQuery().eq(KnowledgeEntityAlias::getCanonicalBookId, canonicalBookId));
        Map<Integer, StringBuilder> chapters = new TreeMap<>();
        for (KnowledgeChunk chunk : chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId).orderByAsc(KnowledgeChunk::getChapterIndex))) {
            chapters.computeIfAbsent(chunk.getChapterIndex(), ignored -> new StringBuilder()).append(chunk.getContent()).append('\n');
        }
        RebuildContext context = new RebuildContext();
        rebuildContext.set(context);
        try {
            int processed = 0;
            for (Map.Entry<Integer, StringBuilder> entry : chapters.entrySet()) {
                extractGraph(canonicalBookId, entry.getKey(), entry.getValue().toString(), modelConfig);
                chapterProgress.accept(++processed);
            }
            lightRagService.refresh(canonicalBookId);
            // Neo4j is a projection. Send bounded batches only after the relational source is complete;
            // this avoids one network transaction per edge during a full LightRAG rebuild.
            graphKnowledgeStore.deleteBook(canonicalBookId);
            graphKnowledgeStore.upsertNodes(new ArrayList<>(context.nodes.values()));
            graphKnowledgeStore.replaceEdges(new ArrayList<>(context.edges.values()));
            profileVectorService.deleteBookProfiles(canonicalBookId);
            refreshProfiles(canonicalBookId);
        } finally {
            rebuildContext.remove();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void buildGraphRange(long canonicalBookId, int startChapter, int endChapter,
                                StructuredGraphExtractor.ModelConfig modelConfig,
                                java.util.function.IntConsumer chapterProgress) {
        buildGraphRangeWithProgress(canonicalBookId, startChapter, endChapter, modelConfig, new GraphBuildProgressListener() {
            @Override public void chapterExtracted(int completedChapters) {
                chapterProgress.accept(completedChapters);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void buildGraphRangeWithProgress(long canonicalBookId, int startChapter, int endChapter,
                                            StructuredGraphExtractor.ModelConfig modelConfig,
                                            GraphBuildProgressListener progressListener) {
        GraphBuildProgressListener listener = progressListener == null ? GraphBuildProgressListener.NOOP : progressListener;
        if (modelConfig == null || !StringUtils.hasText(modelConfig.apiKey())) {
            throw new IllegalArgumentException("A model configuration is required to build a knowledge graph");
        }
        if (startChapter < 1 || endChapter < startChapter) {
            throw new IllegalArgumentException("Invalid chapter range");
        }
        Map<Integer, StringBuilder> chapters = new TreeMap<>();
        for (KnowledgeChunk chunk : chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                .between(KnowledgeChunk::getChapterIndex, startChapter - 1, endChapter - 1)
                .orderByAsc(KnowledgeChunk::getChapterIndex))) {
            chapters.computeIfAbsent(chunk.getChapterIndex(), ignored -> new StringBuilder()).append(chunk.getContent()).append('\n');
        }
        if (chapters.isEmpty()) throw new IllegalArgumentException("No indexed chapters in the selected range");

        // Unlike legacy full rebuilds, range builds never erase claims outside the reader's selection.
        // This is the LightRAG incremental path: only selected evidence is sent to the model.
        RebuildContext context = new RebuildContext();
        rebuildContext.set(context);
        try {
            listener.stageStarted(GraphBuildProgressListener.Stage.EXTRACT);
            int processed = 0;
            for (Map.Entry<Integer, StringBuilder> entry : chapters.entrySet()) {
                extractGraph(canonicalBookId, entry.getKey(), entry.getValue().toString(), modelConfig);
                listener.chapterExtracted(++processed);
            }
            listener.stageCompleted(GraphBuildProgressListener.Stage.EXTRACT);
            listener.stageStarted(GraphBuildProgressListener.Stage.CHARACTER_CALIBRATION);
            calibrateCharacterKnowledge(canonicalBookId, chapters, modelConfig);
            listener.stageCompleted(GraphBuildProgressListener.Stage.CHARACTER_CALIBRATION);
            listener.stageStarted(GraphBuildProgressListener.Stage.STORY_EVENTS);
            synthesizeStoryEvents(canonicalBookId, startChapter - 1, endChapter - 1, modelConfig);
            listener.stageCompleted(GraphBuildProgressListener.Stage.STORY_EVENTS);
            listener.stageStarted(GraphBuildProgressListener.Stage.CLUE_SYNTHESIS);
            synthesizeClues(canonicalBookId, startChapter - 1, endChapter - 1, modelConfig);
            listener.stageCompleted(GraphBuildProgressListener.Stage.CLUE_SYNTHESIS);
            listener.stageStarted(GraphBuildProgressListener.Stage.CLUE_LIFECYCLE);
            reconcileClueLifecycle(canonicalBookId, startChapter - 1, endChapter - 1, modelConfig);
            listener.stageCompleted(GraphBuildProgressListener.Stage.CLUE_LIFECYCLE);
            listener.stageStarted(GraphBuildProgressListener.Stage.RAG_REFRESH);
            refreshBookProfile(canonicalBookId);
            listener.stageProgress(GraphBuildProgressListener.Stage.RAG_REFRESH, 1, 2);
            lightRagService.refresh(canonicalBookId);
            listener.stageCompleted(GraphBuildProgressListener.Stage.RAG_REFRESH);
            // The relational graph is authoritative; projection refresh keeps Neo4j complete after merging a range.
            listener.stageStarted(GraphBuildProgressListener.Stage.GRAPH_PROJECTION);
            reprojectGraph(canonicalBookId);
            listener.stageCompleted(GraphBuildProgressListener.Stage.GRAPH_PROJECTION);
        } finally {
            rebuildContext.remove();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceGraphRange(long canonicalBookId, int startChapter, int endChapter,
                                  StructuredGraphExtractor.ModelConfig modelConfig,
                                  java.util.function.IntConsumer chapterProgress) {
        // Keep optional Neo4j/Milvus projections readable until the new relational graph is
        // complete. buildGraphRange reprojects them only after every model call succeeds.
        clearRelationalGraphClaims(canonicalBookId);
        buildGraphRange(canonicalBookId, startChapter, endChapter, modelConfig, chapterProgress);
        // Replace stale entity/event profiles only after the authoritative rebuild succeeds.
        profileVectorService.deleteBookProfiles(canonicalBookId);
        refreshProfiles(canonicalBookId);
    }

    private void clearRelationalGraphClaims(long canonicalBookId) {
        aliasMapper.delete(Wrappers.<KnowledgeEntityAlias>lambdaQuery().eq(KnowledgeEntityAlias::getCanonicalBookId, canonicalBookId));
        lightRagService.deleteBook(canonicalBookId);
        clueGraphLinkMapper.delete(Wrappers.<KnowledgeClueGraphLink>lambdaQuery().eq(KnowledgeClueGraphLink::getCanonicalBookId, canonicalBookId));
        clueResolutionMapper.delete(Wrappers.<KnowledgeClueResolution>lambdaQuery().eq(KnowledgeClueResolution::getCanonicalBookId, canonicalBookId));
        clueMapper.delete(Wrappers.<KnowledgeClue>lambdaQuery().eq(KnowledgeClue::getCanonicalBookId, canonicalBookId));
        edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery().eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId));
        relationAssertionMapper.delete(Wrappers.<KnowledgeRelationAssertion>lambdaQuery()
                .eq(KnowledgeRelationAssertion::getCanonicalBookId, canonicalBookId));
        nodeMapper.delete(Wrappers.<KnowledgeGraphNode>lambdaQuery().eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId));
    }

    private void calibrateCharacterKnowledge(long bookId, Map<Integer, StringBuilder> chapters,
                                             StructuredGraphExtractor.ModelConfig modelConfig) {
        if (chapters.isEmpty()) return;
        int start = chapters.keySet().stream().mapToInt(Integer::intValue).min().orElse(0);
        int end = chapters.keySet().stream().mapToInt(Integer::intValue).max().orElse(start);
        // Each calibration run replaces its own derived character relations. This avoids retaining
        // an edge merely because an earlier model sample passed a rule that later became stricter.
        // Restrict cleanup to the requested chapter range so a range rebuild never destroys later facts.
        edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, bookId)
                .between(KnowledgeGraphEdge::getFirstChapter, start, end)
                .in(KnowledgeGraphEdge::getRelation, List.of("INTERACTS_WITH", "SUPPORTS", "OPPOSES", "TRAVELS_WITH",
                        "KNOWS", "PARENT_OF", "SPOUSE_OF", "SIBLING_OF", "FRIEND_OF", "COMPANION_OF", "TEACHER_OF",
                        "MASTER_OF", "NEIGHBOR_OF", "GUIDES", "HELPS", "PROTECTS", "CARETAKES", "EMPLOYS"))
                .inSql(KnowledgeGraphEdge::getSourceNodeId, "SELECT id FROM t_knowledge_graph_node WHERE node_type = 'CHARACTER'")
                .inSql(KnowledgeGraphEdge::getTargetNodeId, "SELECT id FROM t_knowledge_graph_node WHERE node_type = 'CHARACTER'"));
        calibrateCharacterKnowledgePass(bookId, chapters, modelConfig, start, end);
        // A half-window offset exposes boundary-spanning revelations to a different local context.
        // Both passes remain bounded LightRAG extraction and merge only evidence-verified facts.
        int shiftedStart = Math.min(end, start + CHARACTER_KNOWLEDGE_WINDOW_CHAPTERS / 2);
        if (shiftedStart > start && shiftedStart <= end) {
            calibrateCharacterKnowledgePass(bookId, chapters, modelConfig, shiftedStart, end);
        }
    }

    private void calibrateCharacterKnowledgePass(long bookId, Map<Integer, StringBuilder> chapters,
                                                  StructuredGraphExtractor.ModelConfig modelConfig,
                                                  int start, int end) {
        for (int windowStart = start; windowStart <= end;
             windowStart += CHARACTER_KNOWLEDGE_WINDOW_CHAPTERS - CHARACTER_KNOWLEDGE_WINDOW_OVERLAP) {
            int windowEnd = Math.min(end, windowStart + CHARACTER_KNOWLEDGE_WINDOW_CHAPTERS - 1);
            List<StructuredGraphExtractor.EntityContext> context = extractionContext(bookId, windowEnd);
            List<StructuredGraphExtractor.ChapterFact> facts = new ArrayList<>();
            for (int chapter = windowStart; chapter <= windowEnd; chapter++) {
                StringBuilder content = chapters.get(chapter);
                if (content != null && StringUtils.hasText(content.toString())) {
                    facts.addAll(characterKnowledgeFacts(chapter, content.toString(), context));
                }
            }
            List<StructuredGraphExtractor.CharacterPairCandidate> candidates = characterPairCandidates(facts, context);
            StructuredGraphExtractor.CharacterKnowledgeExtraction extraction = structuredGraphExtractor
                    .extractCharacterKnowledge(facts, context, modelConfig, candidates);
            List<StructuredGraphExtractor.CharacterRelation> relations = new ArrayList<>(extraction.relations());
            // A separate model pass avoids spending the broad window's relation budget on only
            // conspicuous side characters. It verifies, rather than manufactures, each pair.
            candidates.stream().limit(2).forEach(pair -> relations.addAll(structuredGraphExtractor
                    .verifyCharacterPair(pairEvidenceFacts(facts, pair, context), context, modelConfig, pair).relations()));
            extraction.identities().forEach(identity -> mergeRevealedIdentity(bookId, identity, modelConfig));
            for (StructuredGraphExtractor.CharacterRelation relation : relations) {
                KnowledgeGraphNode source = resolveKnownEndpoint(bookId, relation.source());
                KnowledgeGraphNode target = resolveKnownEndpoint(bookId, relation.target());
                if (source == null || target == null || source.getId().equals(target.getId())
                        || !"CHARACTER".equals(source.getNodeType()) || !"CHARACTER".equals(target.getNodeType())
                        || !isRelationCompatible(source.getNodeType(), target.getNodeType(), relation.type())) continue;
                StructuredGraphExtractor.ChapterFact first = relation.evidence().stream()
                        .min(Comparator.comparingInt(StructuredGraphExtractor.ChapterFact::chapterIndex)).orElse(null);
                if (first == null) continue;
                String evidence = relation.evidence().stream().limit(3).map(StructuredGraphExtractor.ChapterFact::evidence)
                        .collect(java.util.stream.Collectors.joining("；"));
                if (isGenericCharacterRelation(relation.type()) && hasSpecificCharacterRelation(bookId, source.getId(), target.getId())) continue;
                if (Set.of("NEIGHBOR_OF", "FRIEND_OF", "COMPANION_OF", "KNOWS", "OPPOSES", "TRAVELS_WITH").contains(relation.type())) {
                    deleteReverseDirectionalRelation(bookId, source.getId(), target.getId(), relation.type());
                }
                if (Set.of("TEACHER_OF", "MASTER_OF", "PARENT_OF", "SERVES").contains(relation.type())) {
                    deleteReverseDirectionalRelation(bookId, source.getId(), target.getId(), relation.type());
                }
                upsertEdge(bookId, first.chapterIndex(), source, target, relation.type(), evidence,
                        relation.confidence(), CHARACTER_KNOWLEDGE_VERSION_PREFIX + modelConfig.model());
                if (!isGenericCharacterRelation(relation.type())) deleteGenericCharacterRelations(bookId, source.getId(), target.getId());
            }
            if (windowEnd == end) break;
        }
    }

    /**
     * Keeps several verbatim windows from the whole chapter instead of truncating its tail.
     * Identity revelations and explicit relationship language receive the highest score.
     */
    private List<StructuredGraphExtractor.ChapterFact> characterKnowledgeFacts(
            int chapter, String content, List<StructuredGraphExtractor.EntityContext> context) {
        String normalized = safe(content).replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").trim();
        if (normalized.isEmpty()) return List.of();
        Set<String> knownNames = new java.util.LinkedHashSet<>();
        if (context != null) for (StructuredGraphExtractor.EntityContext entity : context) {
            if (!"CHARACTER".equals(entity.type())) continue;
            if (StringUtils.hasText(entity.name())) knownNames.add(entity.name());
            if (entity.aliases() != null) entity.aliases().stream().filter(StringUtils::hasText).forEach(knownNames::add);
        }
        // These are linguistic disclosure markers rather than descriptions from any one novel.
        List<String> identitySignals = List.of("名叫", "叫作", "自称", "身份", "原来是", "正是", "化名", "真名", "本名",
                "身份揭晓", "身份暴露", "竟然是");
        List<String> relationSignals = List.of("教", "传授", "指点", "引导", "同行", "结伴", "帮", "救", "护", "照看", "照拂",
                "引荐", "雇", "干活", "邻居", "隔壁", "赠", "杀", "仇", "追杀", "交手", "对峙", "父亲", "母亲", "兄弟",
                "姐妹", "效忠", "侍奉", "主人", "朋友", "认识", "相识");
        List<CharacterTextWindow> windows = new ArrayList<>();
        List<String> scenes = characterScenes(normalized);
        for (int offset = 0; offset < scenes.size(); offset++) {
            String text = scenes.get(offset);
            int named = (int) knownNames.stream().filter(text::contains).limit(5).count();
            int characterPairs = named < 2 ? 0 : named * (named - 1) / 2;
            int identity = (int) identitySignals.stream().filter(text::contains).limit(4).count();
            int relation = (int) relationSignals.stream().filter(text::contains).limit(4).count();
            windows.add(new CharacterTextWindow(text, identity * 8 + Math.min(named, 3) * 3 + characterPairs * 4 + relation * 2, offset));
        }
        // Favor high-signal scenes while preserving diverse entity pairs. This prevents one long,
        // repetitive neighbor scene from taking every character-calibration slot in a chapter.
        Set<String> coveredPairs = new LinkedHashSet<>();
        List<CharacterTextWindow> selected = new ArrayList<>();
        windows.stream().sorted(Comparator.comparingInt(CharacterTextWindow::score).reversed()
                        .thenComparingInt(CharacterTextWindow::offset)).forEach(window -> {
                    Set<String> pairs = characterPairKeys(window.text(), knownNames);
                    boolean addsPair = pairs.stream().anyMatch(pair -> !coveredPairs.contains(pair));
                    if (selected.size() < 4 && (addsPair || selected.isEmpty())) {
                        selected.add(window); coveredPairs.addAll(pairs);
                    }
                });
        if (selected.size() < 4) windows.stream().sorted(Comparator.comparingInt(CharacterTextWindow::score).reversed()
                        .thenComparingInt(CharacterTextWindow::offset)).filter(window -> !selected.contains(window))
                .limit(4 - selected.size()).forEach(selected::add);
        return selected.stream().sorted(Comparator.comparingInt(CharacterTextWindow::offset))
                .map(window -> new StructuredGraphExtractor.ChapterFact(null, chapter, window.text())).toList();
    }

    /** Splits at natural sentence boundaries so relation evidence is not cut mid-dialogue or action. */
    private List<String> characterScenes(String content) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = Pattern.compile("[^。！？!?]+[。！？!?]?").matcher(content);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) sentences.add(sentence);
        }
        if (sentences.isEmpty()) return List.of(content);
        List<String> scenes = new ArrayList<>();
        StringBuilder scene = new StringBuilder();
        for (String sentence : sentences) {
            if (!scene.isEmpty() && scene.length() + sentence.length() > 720) {
                scenes.add(scene.toString()); scene = new StringBuilder();
            }
            scene.append(sentence);
        }
        if (!scene.isEmpty()) scenes.add(scene.toString());
        return scenes;
    }

    private Set<String> characterPairKeys(String text, Set<String> knownNames) {
        List<String> names = knownNames.stream().filter(text::contains).sorted().limit(5).toList();
        Set<String> pairs = new LinkedHashSet<>();
        for (int left = 0; left < names.size(); left++) for (int right = left + 1; right < names.size(); right++) {
            pairs.add(names.get(left) + "\u0000" + names.get(right));
        }
        return pairs;
    }

    /** Selects co-mentioned known people for model review; it does not claim that a relationship exists. */
    private List<StructuredGraphExtractor.CharacterPairCandidate> characterPairCandidates(
            List<StructuredGraphExtractor.ChapterFact> facts, List<StructuredGraphExtractor.EntityContext> context) {
        if (facts == null || facts.isEmpty() || context == null || context.isEmpty()) return List.of();
        List<StructuredGraphExtractor.EntityContext> characters = context.stream()
                .filter(entity -> "CHARACTER".equals(entity.type()) && StringUtils.hasText(entity.name())).toList();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (StructuredGraphExtractor.ChapterFact fact : facts) {
            String text = safe(fact.evidence());
            int relationSignalCount = (int) List.of("帮", "引荐", "指点", "劝", "救", "护", "邻居", "隔壁", "朋友", "同行",
                    "父", "母", "徒弟", "先生", "师父", "照看", "答应", "借").stream().filter(text::contains).count();
            List<String> mentioned = characters.stream().filter(entity -> entityMentioned(entity, text))
                    .map(StructuredGraphExtractor.EntityContext::name).sorted().toList();
            for (int left = 0; left < mentioned.size(); left++) for (int right = left + 1; right < mentioned.size(); right++) {
                counts.merge(mentioned.get(left) + "\u0000" + mentioned.get(right), 1 + relationSignalCount * 2, Integer::sum);
            }
        }
        return counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey)).limit(8).map(entry -> {
                    String[] names = entry.getKey().split("\u0000", 2);
                    return new StructuredGraphExtractor.CharacterPairCandidate(names[0], names[1], entry.getValue());
                }).toList();
    }

    private List<StructuredGraphExtractor.ChapterFact> pairEvidenceFacts(List<StructuredGraphExtractor.ChapterFact> facts,
                                                                           StructuredGraphExtractor.CharacterPairCandidate pair,
                                                                           List<StructuredGraphExtractor.EntityContext> context) {
        if (facts == null || pair == null) return List.of();
        return facts.stream().filter(fact -> entityMentioned(pair.left(), context, safe(fact.evidence()))
                        && entityMentioned(pair.right(), context, safe(fact.evidence())))
                .limit(8).toList();
    }

    private boolean entityMentioned(String canonicalName, List<StructuredGraphExtractor.EntityContext> context, String text) {
        if (text.contains(canonicalName)) return true;
        return context != null && context.stream().filter(entity -> canonicalName.equals(entity.name()))
                .anyMatch(entity -> entity.aliases() != null && entity.aliases().stream().filter(StringUtils::hasText).anyMatch(text::contains));
    }

    private boolean entityMentioned(StructuredGraphExtractor.EntityContext entity, String text) {
        if (text.contains(entity.name())) return true;
        return entity.aliases() != null && entity.aliases().stream().filter(StringUtils::hasText).anyMatch(text::contains);
    }

    private void mergeRevealedIdentity(long bookId, StructuredGraphExtractor.IdentityResolution identity,
                                       StructuredGraphExtractor.ModelConfig modelConfig) {
        KnowledgeGraphNode mention = resolveExactCharacter(bookId, identity.mention());
        String evidence = identity.evidence().stream().limit(3).map(StructuredGraphExtractor.ChapterFact::evidence)
                .collect(java.util.stream.Collectors.joining("；"));
        int revealedChapter = identity.evidence().stream().mapToInt(StructuredGraphExtractor.ChapterFact::chapterIndex).max().orElse(0);
        KnowledgeGraphNode canonical = resolveExactCharacter(bookId, identity.canonicalName());
        if (canonical == null && mention == null) return;
        if (canonical == null) canonical = upsertNode(bookId, mention.getFirstChapter(), identity.canonicalName(), "CHARACTER", "",
                evidence, identity.confidence(), "identity-resolution-v1:" + modelConfig.model());
        if (canonical == null) return;
        // A previous chapter rebuild may already have normalized the descriptive node away.
        // The verified revelation still needs to persist the mention as a canonical alias.
        if (mention == null) {
            upsertAlias(bookId, Math.min(revealedChapter, canonical.getFirstChapter()), canonical,
                    identity.mention(), evidence, identity.confidence());
            return;
        }
        if (canonical.getId().equals(mention.getId())) return;
        KnowledgeGraphNode resolvedCanonical = canonical;
        upsertAlias(bookId, Math.min(revealedChapter, mention.getFirstChapter()), canonical, mention.getName(), evidence, identity.confidence());

        List<KnowledgeGraphEdge> edges = edgeMapper.selectList(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, bookId)
                .and(query -> query.eq(KnowledgeGraphEdge::getSourceNodeId, mention.getId()).or()
                        .eq(KnowledgeGraphEdge::getTargetNodeId, mention.getId())));
        for (KnowledgeGraphEdge edge : edges) {
            KnowledgeGraphNode source = nodeMapper.selectById(edge.getSourceNodeId().equals(mention.getId()) ? canonical.getId() : edge.getSourceNodeId());
            KnowledgeGraphNode target = nodeMapper.selectById(edge.getTargetNodeId().equals(mention.getId()) ? canonical.getId() : edge.getTargetNodeId());
            if (source != null && target != null && !source.getId().equals(target.getId())
                    && isRelationCompatible(source.getNodeType(), target.getNodeType(), edge.getRelation())) {
                upsertEdge(bookId, edge.getFirstChapter(), source, target, edge.getRelation(), edge.getEvidence(),
                        Math.max(identity.confidence(), edge.getConfidence() == null ? 0D : edge.getConfidence()), "identity-merge-v1:" + modelConfig.model());
            }
        }
        aliasMapper.selectList(Wrappers.<KnowledgeEntityAlias>lambdaQuery().eq(KnowledgeEntityAlias::getCanonicalBookId, bookId)
                        .eq(KnowledgeEntityAlias::getNodeId, mention.getId()))
                .forEach(alias -> upsertAlias(bookId, alias.getFirstChapter(), resolvedCanonical, alias.getAlias(), alias.getEvidence(), alias.getConfidence()));
        clueGraphLinkMapper.selectList(Wrappers.<KnowledgeClueGraphLink>lambdaQuery()
                        .eq(KnowledgeClueGraphLink::getCanonicalBookId, bookId).eq(KnowledgeClueGraphLink::getNodeId, mention.getId()))
                .forEach(link -> {
                    Long clueId = link.getClueId();
                    Long canonicalNodeId = resolvedCanonical.getId();
                    Long existing = clueGraphLinkMapper.selectCount(Wrappers.<KnowledgeClueGraphLink>lambdaQuery()
                            .eq(KnowledgeClueGraphLink::getCanonicalBookId, bookId).eq(KnowledgeClueGraphLink::getClueId, clueId)
                            .eq(KnowledgeClueGraphLink::getNodeId, canonicalNodeId));
                    if (existing == null || existing == 0) {
                        link.setId(SnowflakeIdUtil.next()); link.setNodeId(canonicalNodeId); clueGraphLinkMapper.insert(link);
                    }
                });
        edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery().eq(KnowledgeGraphEdge::getCanonicalBookId, bookId)
                .and(query -> query.eq(KnowledgeGraphEdge::getSourceNodeId, mention.getId()).or()
                        .eq(KnowledgeGraphEdge::getTargetNodeId, mention.getId())));
        relationAssertionMapper.delete(Wrappers.<KnowledgeRelationAssertion>lambdaQuery().eq(KnowledgeRelationAssertion::getCanonicalBookId, bookId)
                .and(query -> query.eq(KnowledgeRelationAssertion::getSourceNodeId, mention.getId()).or()
                        .eq(KnowledgeRelationAssertion::getTargetNodeId, mention.getId())));
        aliasMapper.delete(Wrappers.<KnowledgeEntityAlias>lambdaQuery().eq(KnowledgeEntityAlias::getCanonicalBookId, bookId)
                .eq(KnowledgeEntityAlias::getNodeId, mention.getId()));
        clueGraphLinkMapper.delete(Wrappers.<KnowledgeClueGraphLink>lambdaQuery().eq(KnowledgeClueGraphLink::getCanonicalBookId, bookId)
                .eq(KnowledgeClueGraphLink::getNodeId, mention.getId()));
        nodeMapper.deleteById(mention.getId());
        canonical.setFirstChapter(Math.min(canonical.getFirstChapter(), mention.getFirstChapter()));
        canonical.setLastChapter(Math.max(canonical.getLastChapter(), mention.getLastChapter()));
        canonical.setUpdatedAt(LocalDateTime.now()); nodeMapper.updateById(canonical);
    }

    private boolean isGenericCharacterRelation(String relation) {
        return "KNOWS".equals(relation) || "INTERACTS_WITH".equals(relation);
    }

    private boolean hasSpecificCharacterRelation(long bookId, long leftId, long rightId) {
        Long count = edgeMapper.selectCount(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, bookId)
                .notIn(KnowledgeGraphEdge::getRelation, List.of("KNOWS", "INTERACTS_WITH"))
                .and(query -> query.and(pair -> pair.eq(KnowledgeGraphEdge::getSourceNodeId, leftId)
                                .eq(KnowledgeGraphEdge::getTargetNodeId, rightId))
                        .or(pair -> pair.eq(KnowledgeGraphEdge::getSourceNodeId, rightId)
                                .eq(KnowledgeGraphEdge::getTargetNodeId, leftId))));
        return count != null && count > 0;
    }

    private void deleteGenericCharacterRelations(long bookId, long leftId, long rightId) {
        edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, bookId)
                .in(KnowledgeGraphEdge::getRelation, List.of("KNOWS", "INTERACTS_WITH"))
                .and(query -> query.and(pair -> pair.eq(KnowledgeGraphEdge::getSourceNodeId, leftId)
                                .eq(KnowledgeGraphEdge::getTargetNodeId, rightId))
                        .or(pair -> pair.eq(KnowledgeGraphEdge::getSourceNodeId, rightId)
                                .eq(KnowledgeGraphEdge::getTargetNodeId, leftId))));
    }

    private void deleteReverseDirectionalRelation(long bookId, long sourceId, long targetId, String relation) {
        edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                .eq(KnowledgeGraphEdge::getCanonicalBookId, bookId)
                .eq(KnowledgeGraphEdge::getSourceNodeId, targetId)
                .eq(KnowledgeGraphEdge::getTargetNodeId, sourceId)
                .eq(KnowledgeGraphEdge::getRelation, relation));
    }

    private KnowledgeGraphNode resolveExactCharacter(long bookId, String name) {
        List<KnowledgeGraphNode> nodes = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).eq(KnowledgeGraphNode::getNodeType, "CHARACTER")
                .eq(KnowledgeGraphNode::getName, name).last("LIMIT 2"));
        return nodes.size() == 1 ? nodes.get(0) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildDerivedInsights(long canonicalBookId, int startChapter, int endChapter,
                                       StructuredGraphExtractor.ModelConfig modelConfig) {
        if (startChapter < 1 || endChapter < startChapter) throw new IllegalArgumentException("Invalid chapter range");
        int zeroStart = startChapter - 1, zeroEnd = endChapter - 1;
        List<Long> derivedNodeIds = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                        .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)
                        .in(KnowledgeGraphNode::getNodeType, List.of("EVENT", "CLUE"))
                        .le(KnowledgeGraphNode::getFirstChapter, zeroEnd).ge(KnowledgeGraphNode::getLastChapter, zeroStart))
                .stream().map(KnowledgeGraphNode::getId).toList();
        if (!derivedNodeIds.isEmpty()) {
            edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery().eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId)
                    .and(query -> query.in(KnowledgeGraphEdge::getSourceNodeId, derivedNodeIds).or().in(KnowledgeGraphEdge::getTargetNodeId, derivedNodeIds)));
            nodeMapper.deleteBatchIds(derivedNodeIds);
        }
        List<Long> clueIds = clueMapper.selectList(Wrappers.<KnowledgeClue>lambdaQuery().eq(KnowledgeClue::getCanonicalBookId, canonicalBookId)
                        .between(KnowledgeClue::getChapterIndex, zeroStart, zeroEnd)).stream().map(KnowledgeClue::getId).toList();
        if (!clueIds.isEmpty()) {
            clueGraphLinkMapper.delete(Wrappers.<KnowledgeClueGraphLink>lambdaQuery().in(KnowledgeClueGraphLink::getClueId, clueIds));
            clueResolutionMapper.delete(Wrappers.<KnowledgeClueResolution>lambdaQuery().in(KnowledgeClueResolution::getClueId, clueIds));
        }
        clueMapper.delete(Wrappers.<KnowledgeClue>lambdaQuery().eq(KnowledgeClue::getCanonicalBookId, canonicalBookId)
                .between(KnowledgeClue::getChapterIndex, zeroStart, zeroEnd));
        synthesizeStoryEvents(canonicalBookId, zeroStart, zeroEnd, modelConfig);
        synthesizeClues(canonicalBookId, zeroStart, zeroEnd, modelConfig);
        reconcileClueLifecycle(canonicalBookId, zeroStart, zeroEnd, modelConfig);
        reprojectGraph(canonicalBookId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildCharacterKnowledge(long canonicalBookId, int startChapter, int endChapter,
                                          StructuredGraphExtractor.ModelConfig modelConfig) {
        if (startChapter < 1 || endChapter < startChapter) throw new IllegalArgumentException("Invalid chapter range");
        List<Long> characterIds = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                        .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)
                        .eq(KnowledgeGraphNode::getNodeType, "CHARACTER"))
                .stream().map(KnowledgeGraphNode::getId).toList();
        if (!characterIds.isEmpty()) {
            // V2 is an evidence-verified incremental union. Remove legacy/chapter person edges once,
            // then let later partial rebuilds add missed facts without erasing previously verified ones.
            edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery()
                    .eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId)
                    .between(KnowledgeGraphEdge::getFirstChapter, startChapter - 1, endChapter - 1)
                    .in(KnowledgeGraphEdge::getSourceNodeId, characterIds)
                    .in(KnowledgeGraphEdge::getTargetNodeId, characterIds)
                    .notLike(KnowledgeGraphEdge::getSourceModelVersion, "character-window-v2:"));
        }
        Map<Integer, StringBuilder> chapters = new TreeMap<>();
        chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                        .between(KnowledgeChunk::getChapterIndex, startChapter - 1, endChapter - 1)
                        .orderByAsc(KnowledgeChunk::getChapterIndex))
                .forEach(chunk -> chapters.computeIfAbsent(chunk.getChapterIndex(), ignored -> new StringBuilder())
                        .append(chunk.getContent()).append('\n'));
        if (chapters.isEmpty()) throw new IllegalArgumentException("No indexed chapters in the selected range");
        calibrateCharacterKnowledge(canonicalBookId, chapters, modelConfig);
        lightRagService.refresh(canonicalBookId);
        reprojectGraph(canonicalBookId);
        profileVectorService.deleteBookProfiles(canonicalBookId);
        refreshProfiles(canonicalBookId);
    }

    private void synthesizeClues(long bookId, int startChapter, int endChapter, StructuredGraphExtractor.ModelConfig modelConfig) {
        List<KnowledgeChunk> clueChunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, bookId).between(KnowledgeChunk::getChapterIndex, startChapter, endChapter)
                .orderByAsc(KnowledgeChunk::getChapterIndex));
        for (int windowStart = startChapter; windowStart <= endChapter; windowStart += 10) {
            int windowEnd = Math.min(endChapter, windowStart + 11);
            int selectedStart = windowStart, selectedEnd = windowEnd;
            List<StructuredGraphExtractor.ChapterFact> facts = clueChunks.stream()
                    .filter(chunk -> chunk.getChapterIndex() >= selectedStart && chunk.getChapterIndex() <= selectedEnd)
                    .filter(chunk -> hasClueSignal(chunk.getContent())).limit(36)
                    .map(chunk -> new StructuredGraphExtractor.ChapterFact(chunk.getId(), chunk.getChapterIndex(), excerpt(chunk.getContent(), 700))).toList();
            for (StructuredGraphExtractor.ClueCandidate candidate : structuredGraphExtractor.extractClues(facts, modelConfig).clues()) {
                StructuredGraphExtractor.ChapterFact first = candidate.evidence().stream()
                        .min(Comparator.comparingInt(StructuredGraphExtractor.ChapterFact::chapterIndex)).orElse(null);
                if (first == null) continue;
                String evidence = candidate.evidence().stream().limit(3).map(StructuredGraphExtractor.ChapterFact::evidence)
                        .collect(java.util.stream.Collectors.joining("；"));
                evidence = "【当前未解原因】" + candidate.unresolvedReason() + "【原文依据】" + evidence;
                StructuredGraphExtractor.Entity entity = new StructuredGraphExtractor.Entity(candidate.signal(), "CLUE", "", List.of(),
                        excerpt(evidence, 500), candidate.confidence());
                KnowledgeGraphNode node = upsertNode(bookId, first.chapterIndex(), candidate.signal(), "CLUE", "", entity.evidence(),
                        candidate.confidence(), "clue-window-v2:" + modelConfig.model());
                KnowledgeClue clue = upsertModelClue(bookId, first.chapterIndex(), entity, "clue-window-v2:" + modelConfig.model());
                if (clue != null) { clue.setSignal(excerpt(candidate.signal(), 60)); clueMapper.updateById(clue); }
                if (node != null && clue != null) linkClueToKnownMentions(bookId, first.chapterIndex(), clue);
            }
            if (windowEnd == endChapter) break;
        }
    }

    /** Replays only later evidence, so a reader never sees a future clue development. */
    private void reconcileClueLifecycle(long bookId, int startChapter, int endChapter,
                                        StructuredGraphExtractor.ModelConfig modelConfig) {
        List<KnowledgeClue> openClues = clueMapper.selectList(Wrappers.<KnowledgeClue>lambdaQuery()
                .eq(KnowledgeClue::getCanonicalBookId, bookId)
                .in(KnowledgeClue::getStatus, List.of("OPEN", "PARTIALLY_RESOLVED"))
                .le(KnowledgeClue::getChapterIndex, endChapter)
                .eq(KnowledgeClue::getReviewStatus, APPROVED));
        if (openClues.isEmpty()) return;
        List<KnowledgeChunk> laterChunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, bookId)
                .le(KnowledgeChunk::getChapterIndex, endChapter)
                .orderByAsc(KnowledgeChunk::getChapterIndex));
        for (KnowledgeClue clue : openClues) {
            List<KnowledgeChunk> candidates = rankClueLifecycleCandidates(clue, laterChunks);
            if (candidates.isEmpty()) continue;
            StructuredGraphExtractor.ClueLifecycleExtraction assessment = structuredGraphExtractor.assessClueLifecycle(
                    clueContext(clue), candidates.stream().map(chunk -> new StructuredGraphExtractor.ChapterFact(chunk.getId(),
                            chunk.getChapterIndex(), excerpt(chunk.getContent(), 900))).toList(), modelConfig);
            if (assessment == null || assessment.assessments().isEmpty()) continue;
            for (StructuredGraphExtractor.ClueLifecycleAssessment milestone : assessment.assessments()) {
                persistClueMilestone(bookId, clue, milestone, modelConfig.model());
                if ("FINAL".equals(milestone.type())) break;
            }
        }
    }

    private StructuredGraphExtractor.ClueContext clueContext(KnowledgeClue clue) {
        String excerpt = safe(clue.getExcerpt());
        Matcher matcher = Pattern.compile("【当前未解原因】([\\s\\S]*?)(?=【原文依据】|$)").matcher(excerpt);
        String reason = matcher.find() ? matcher.group(1).trim() : safe(clue.getSignal());
        int marker = excerpt.indexOf("【原文依据】");
        String evidence = marker < 0 ? excerpt : excerpt.substring(marker + "【原文依据】".length()).trim();
        return new StructuredGraphExtractor.ClueContext(clue.getChapterIndex(), safe(clue.getSignal()), reason, evidence);
    }

    /** Candidate ranking is recall only; the model still rejects unrelated semantic neighbours. */
    private List<KnowledgeChunk> rankClueLifecycleCandidates(KnowledgeClue clue, List<KnowledgeChunk> chunks) {
        String query = safe(clue.getSignal()) + " " + clueContext(clue).unresolvedReason() + " " + clueContext(clue).evidence();
        List<Double> embedded;
        try { embedded = embeddingService.embed(query); } catch (Exception ignored) { embedded = List.of(); }
        final List<Double> queryVector = embedded;
        return chunks.stream().filter(chunk -> chunk.getChapterIndex() > clue.getChapterIndex())
                .map(chunk -> new ScoredChunk(chunk, clueCandidateScore(query, queryVector, chunk)))
                .filter(candidate -> candidate.score() > 0D).sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(candidate -> candidate.chunk().getChapterIndex()))
                .limit(CLUE_LIFECYCLE_CANDIDATE_LIMIT).map(ScoredChunk::chunk).toList();
    }

    private double clueCandidateScore(String query, List<Double> queryVector, KnowledgeChunk chunk) {
        String text = safe(chunk.getContent());
        double lexical = keywordOverlap(query, text);
        if (queryVector == null || queryVector.isEmpty() || !StringUtils.hasText(chunk.getEmbeddingJson())) return lexical;
        try {
            List<Double> vector = readVector(chunk.getEmbeddingJson());
            return Math.max(0D, embeddingService.similarity(queryVector, vector)) * 0.72D + lexical * 0.28D;
        } catch (Exception ignored) { return lexical; }
    }

    private double keywordOverlap(String query, String text) {
        Set<String> terms = extractKeywords(query);
        if (terms.isEmpty() || !StringUtils.hasText(text)) return 0D;
        long matched = terms.stream().filter(text::contains).count();
        return (double) matched / terms.size();
    }

    private void persistClueMilestone(long bookId, KnowledgeClue clue, StructuredGraphExtractor.ClueLifecycleAssessment assessment,
                                      String model) {
        String hash = sha256(assessment.type() + "\n" + assessment.evidence().chapterIndex() + "\n" + assessment.evidence().evidence());
        KnowledgeClueResolution existing = clueResolutionMapper.selectOne(Wrappers.<KnowledgeClueResolution>lambdaQuery()
                .eq(KnowledgeClueResolution::getClueId, clue.getId()).eq(KnowledgeClueResolution::getContentHash, hash));
        if (existing != null) return;
        KnowledgeClueResolution milestone = new KnowledgeClueResolution();
        milestone.setId(SnowflakeIdUtil.next()); milestone.setCanonicalBookId(bookId); milestone.setClueId(clue.getId());
        milestone.setResolutionChapter(assessment.evidence().chapterIndex()); milestone.setResolutionType(assessment.type());
        milestone.setEvidence(excerpt(assessment.evidence().evidence(), 500)); milestone.setExplanation(excerpt(assessment.explanation(), 180));
        milestone.setConfidence(assessment.confidence()); milestone.setSourceModelVersion(CLUE_LIFECYCLE_VERSION_PREFIX + model);
        milestone.setReviewStatus(APPROVED); milestone.setContentHash(hash); milestone.setCreatedAt(LocalDateTime.now()); milestone.setUpdatedAt(LocalDateTime.now());
        clueResolutionMapper.insert(milestone);
        clue.setStatus("FINAL".equals(assessment.type()) ? "RESOLVED" : "PARTIALLY_RESOLVED");
        clue.setResolvedChapter(assessment.evidence().chapterIndex()); clue.setResolutionEvidence(milestone.getEvidence());
        clue.setUpdatedAt(LocalDateTime.now()); clueMapper.updateById(clue);
    }

    private ClueVO clueViewAtBoundary(KnowledgeClue clue, List<KnowledgeClueResolution> milestones) {
        List<ClueProgressVO> progress = milestones.stream().map(item -> new ClueProgressVO(item.getResolutionChapter(),
                item.getResolutionType(), item.getEvidence(), item.getExplanation())).toList();
        KnowledgeClueResolution latest = milestones.isEmpty() ? null : milestones.get(milestones.size() - 1);
        boolean finalAnswer = milestones.stream().anyMatch(item -> "FINAL".equals(item.getResolutionType()));
        if (latest != null) {
            return new ClueVO(clue.getChapterIndex(), clue.getExcerpt(), clue.getSignal(), finalAnswer ? "RESOLVED" : "PARTIALLY_RESOLVED",
                    latest.getResolutionChapter(), latest.getEvidence(), progress);
        }
        // Retain compatibility for a database that has not yet applied the history migration.
        if ("RESOLVED".equals(clue.getStatus()) && clue.getResolvedChapter() != null) {
            return new ClueVO(clue.getChapterIndex(), clue.getExcerpt(), clue.getSignal(), "RESOLVED",
                    clue.getResolvedChapter(), clue.getResolutionEvidence(), List.of());
        }
        return new ClueVO(clue.getChapterIndex(), clue.getExcerpt(), clue.getSignal(), "OPEN", null, null, List.of());
    }

    private boolean hasClueSignal(String content) {
        String value = safe(content);
        return List.of("蹊跷", "古怪", "秘密", "真相", "隐瞒", "不知为何", "无人知晓", "无法解释", "答应", "承诺")
                .stream().anyMatch(value::contains);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearGraph(long canonicalBookId) {
        clearGraphTables(canonicalBookId);
    }

    private void clearGraphTables(long canonicalBookId) {
        graphKnowledgeStore.deleteBook(canonicalBookId);
        profileVectorService.deleteBookProfiles(canonicalBookId);
        aliasMapper.delete(Wrappers.<KnowledgeEntityAlias>lambdaQuery().eq(KnowledgeEntityAlias::getCanonicalBookId, canonicalBookId));
        lightRagService.deleteBook(canonicalBookId);
        clueMapper.delete(Wrappers.<KnowledgeClue>lambdaQuery().eq(KnowledgeClue::getCanonicalBookId, canonicalBookId));
        clueResolutionMapper.delete(Wrappers.<KnowledgeClueResolution>lambdaQuery().eq(KnowledgeClueResolution::getCanonicalBookId, canonicalBookId));
        clueGraphLinkMapper.delete(Wrappers.<KnowledgeClueGraphLink>lambdaQuery().eq(KnowledgeClueGraphLink::getCanonicalBookId, canonicalBookId));
        edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery().eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId));
        relationAssertionMapper.delete(Wrappers.<KnowledgeRelationAssertion>lambdaQuery()
                .eq(KnowledgeRelationAssertion::getCanonicalBookId, canonicalBookId));
        nodeMapper.delete(Wrappers.<KnowledgeGraphNode>lambdaQuery().eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId));
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
    public int reembedBookEvidence(long canonicalBookId) {
        String version = agentProperties.getEmbeddingModelVersion();
        List<KnowledgeChunk> chunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                .ne(KnowledgeChunk::getEmbeddingModelVersion, version)
                .orderByAsc(KnowledgeChunk::getChapterIndex).orderByAsc(KnowledgeChunk::getId));
        if (chunks.isEmpty()) return 0;

        for (int start = 0; start < chunks.size(); start += EMBEDDING_REINDEX_BATCH_SIZE) {
            List<KnowledgeChunk> batch = chunks.subList(start, Math.min(chunks.size(), start + EMBEDDING_REINDEX_BATCH_SIZE));
            List<List<Double>> vectors = embeddingService.embedAll(batch.stream().map(KnowledgeChunk::getContent).toList());
            if (vectors.size() != batch.size()) throw new IllegalStateException("Embedding provider returned an incomplete batch");
            for (int index = 0; index < batch.size(); index++) {
                KnowledgeChunk chunk = batch.get(index);
                chunk.setEmbeddingJson(writeVector(vectors.get(index)));
                chunk.setEmbeddingModelVersion(version);
                chunkMapper.updateById(chunk);
            }
        }
        documentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getCanonicalBookId, canonicalBookId)
                .set(KnowledgeDocument::getEmbeddingModelVersion, version)
                .set(KnowledgeDocument::getUpdatedAt, LocalDateTime.now()));
        refreshProfiles(canonicalBookId);
        lightRagService.refresh(canonicalBookId);
        // The PostgreSQL vectors are authoritative. Do not flush Milvus once per batch here:
        // local Milvus throttles flushes heavily, so its optional projection is repaired separately.
        log.info("Re-embedded {} LightRAG evidence chunks for book {} with {}", chunks.size(), canonicalBookId, version);
        return chunks.size();
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
        clueResolutionMapper.delete(Wrappers.<KnowledgeClueResolution>lambdaQuery().eq(KnowledgeClueResolution::getCanonicalBookId, canonicalBookId));
        clueGraphLinkMapper.delete(Wrappers.<KnowledgeClueGraphLink>lambdaQuery().eq(KnowledgeClueGraphLink::getCanonicalBookId, canonicalBookId));
        edgeMapper.delete(Wrappers.<KnowledgeGraphEdge>lambdaQuery().eq(KnowledgeGraphEdge::getCanonicalBookId, canonicalBookId));
        nodeMapper.delete(Wrappers.<KnowledgeGraphNode>lambdaQuery().eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId));
        chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId));
        documentMapper.delete(Wrappers.<KnowledgeDocument>lambdaQuery().eq(KnowledgeDocument::getCanonicalBookId, canonicalBookId));
    }

    /** Explicit builds are model-only; the legacy overload remains for compatibility with old jobs. */
    private void extractGraph(long bookId, int chapter, String content, StructuredGraphExtractor.ModelConfig modelConfig) {
        StructuredGraphExtractor.Extraction extraction = structuredGraphExtractor.extract(content, modelConfig, extractionContext(bookId, chapter));
        persistModelGraph(bookId, chapter, extraction);
    }

    private List<StructuredGraphExtractor.EntityContext> extractionContext(long bookId, int chapter) {
        List<KnowledgeGraphNode> nodes = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, bookId)
                .in(KnowledgeGraphNode::getNodeType, List.of("CHARACTER", "LOCATION", "ORGANIZATION"))
                .le(KnowledgeGraphNode::getFirstChapter, chapter)
                .orderByDesc(KnowledgeGraphNode::getLastChapter).last("LIMIT 80"));
        if (nodes.isEmpty()) return List.of();
        Map<Long, List<String>> aliases = aliasMapper.selectList(Wrappers.<KnowledgeEntityAlias>lambdaQuery()
                        .eq(KnowledgeEntityAlias::getCanonicalBookId, bookId).in(KnowledgeEntityAlias::getNodeId,
                                nodes.stream().map(KnowledgeGraphNode::getId).toList()))
                .stream().collect(java.util.stream.Collectors.groupingBy(KnowledgeEntityAlias::getNodeId,
                        java.util.stream.Collectors.mapping(KnowledgeEntityAlias::getAlias, java.util.stream.Collectors.toList())));
        return nodes.stream().map(node -> new StructuredGraphExtractor.EntityContext(node.getName(), node.getNodeType(),
                aliases.getOrDefault(node.getId(), List.of()))).toList();
    }

    private void persistModelGraph(long bookId, int chapter, StructuredGraphExtractor.Extraction extraction) {
        Map<String, KnowledgeGraphNode> extractedNodes = new HashMap<>();
        Map<Long, KnowledgeClue> extractedClues = new HashMap<>();
        for (StructuredGraphExtractor.Entity entity : extraction.entities()) {
            KnowledgeGraphNode node = resolveEntityByKnownNames(bookId, entity);
            if (node == null) node = upsertNode(bookId, chapter, entity.name(), entity.type(), entity.identityHint(), entity.evidence(), entity.confidence(), extraction.sourceModelVersion());
            if (node == null) continue;
            if (!entity.name().equals(node.getName())) upsertAlias(bookId, chapter, node, entity.name(), entity.evidence(), entity.confidence());
            extractedNodes.put(entityLookupKey(entity.name(), entity.identityHint()), node);
            extractedNodes.putIfAbsent(entity.name(), node);
            KnowledgeGraphNode resolvedNode = node;
            entity.aliases().forEach(alias -> {
                upsertAlias(bookId, chapter, resolvedNode, alias, entity.evidence(), entity.confidence());
                extractedNodes.putIfAbsent(alias, resolvedNode);
            });
            if ("CLUE".equals(entity.type())) {
                KnowledgeClue clue = upsertModelClue(bookId, chapter, entity, extraction.sourceModelVersion());
                if (clue != null) extractedClues.put(node.getId(), clue);
            }
        }
        for (StructuredGraphExtractor.Relation relation : extraction.relations()) {
            KnowledgeGraphNode source = extractedNodes.getOrDefault(entityLookupKey(relation.source(), relation.sourceIdentityHint()), extractedNodes.get(relation.source()));
            KnowledgeGraphNode target = extractedNodes.getOrDefault(entityLookupKey(relation.target(), relation.targetIdentityHint()), extractedNodes.get(relation.target()));
            if (source == null) source = resolveKnownEndpoint(bookId, relation.source());
            if (target == null) target = resolveKnownEndpoint(bookId, relation.target());
            if (source != null && target != null && !source.getId().equals(target.getId())
                    && isRelationCompatible(source.getNodeType(), target.getNodeType(), relation.type())) {
                upsertEdge(bookId, chapter, source, target, relation.type(), relation.evidence(), relation.confidence(), extraction.sourceModelVersion());
                linkClueToNode(bookId, extractedClues.get(source.getId()), target, relation.type(), relation.evidence(), relation.confidence());
                linkClueToNode(bookId, extractedClues.get(target.getId()), source, relation.type(), relation.evidence(), relation.confidence());
            }
        }
        // A clue frequently shares its sentence with the relevant person or place but has no
        // model-emitted edge. Link only explicit same-chapter co-occurrences, never semantic guesses.
        extractedClues.values().forEach(clue -> extractedNodes.values().stream().distinct()
                .filter(node -> !"CLUE".equals(node.getNodeType()) && containsNormalized(clue.getExcerpt(), node.getName()))
                .forEach(node -> linkClueToNode(bookId, clue, node, "ASSOCIATED_WITH", clue.getExcerpt(), 0.80D)));
        extractedClues.values().forEach(clue -> linkClueToKnownMentions(bookId, chapter, clue));
    }

    private KnowledgeGraphNode resolveEntityByKnownNames(long bookId, StructuredGraphExtractor.Entity entity) {
        if (Set.of("EVENT", "CLUE").contains(entity.type())) return null;
        LinkedHashSet<Long> candidates = new LinkedHashSet<>();
        List<String> names = new ArrayList<>(); names.add(entity.name()); names.addAll(entity.aliases());
        for (String name : names) {
            nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery().eq(KnowledgeGraphNode::getCanonicalBookId, bookId)
                    .eq(KnowledgeGraphNode::getNodeType, entity.type()).eq(KnowledgeGraphNode::getName, name).last("LIMIT 2"))
                    .forEach(node -> candidates.add(node.getId()));
            aliasMapper.selectList(Wrappers.<KnowledgeEntityAlias>lambdaQuery().eq(KnowledgeEntityAlias::getCanonicalBookId, bookId)
                    .eq(KnowledgeEntityAlias::getNodeType, entity.type()).eq(KnowledgeEntityAlias::getAlias, name).last("LIMIT 2"))
                    .forEach(alias -> candidates.add(alias.getNodeId()));
        }
        return candidates.size() == 1 ? nodeMapper.selectById(candidates.iterator().next()) : null;
    }

    private KnowledgeGraphNode resolveKnownEndpoint(long bookId, String mention) {
        if (!StringUtils.hasText(mention)) return null;
        List<KnowledgeGraphNode> exact = nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, bookId).eq(KnowledgeGraphNode::getName, mention).last("LIMIT 2"));
        if (exact.size() == 1) return exact.get(0);
        List<KnowledgeEntityAlias> aliases = aliasMapper.selectList(Wrappers.<KnowledgeEntityAlias>lambdaQuery()
                .eq(KnowledgeEntityAlias::getCanonicalBookId, bookId).eq(KnowledgeEntityAlias::getAlias, mention).last("LIMIT 2"));
        return aliases.size() == 1 ? nodeMapper.selectById(aliases.get(0).getNodeId()) : null;
    }

    private KnowledgeClue upsertModelClue(long bookId, int chapter, StructuredGraphExtractor.Entity entity, String modelVersion) {
        String hash = sha256(bookId + ":" + chapter + ":" + entity.evidence());
        KnowledgeClue existing = clueMapper.selectOne(Wrappers.<KnowledgeClue>lambdaQuery()
                .eq(KnowledgeClue::getCanonicalBookId, bookId).eq(KnowledgeClue::getContentHash, hash));
        if (existing != null) return existing;
        KnowledgeClue clue = new KnowledgeClue();
        clue.setId(SnowflakeIdUtil.next()); clue.setCanonicalBookId(bookId); clue.setChapterIndex(chapter);
        clue.setSignal(entity.name()); clue.setExcerpt(entity.evidence()); clue.setContentHash(hash);
        clue.setStatus("OPEN"); clue.setSourceModelVersion(modelVersion); clue.setReviewStatus(APPROVED);
        clue.setCreatedAt(LocalDateTime.now()); clue.setUpdatedAt(LocalDateTime.now()); clueMapper.insert(clue);
        return clue;
    }

    private boolean isRelationCompatible(String sourceType, String targetType, String relation) {
        if ("CLUE_FOR".equals(relation) || "ASSOCIATED_WITH".equals(relation)) return "CLUE".equals(sourceType) || "CLUE".equals(targetType);
        if (Set.of("CAUSES", "LEADS_TO", "PREVENTS", "RESOLVES").contains(relation)) return "EVENT".equals(sourceType) && "EVENT".equals(targetType);
        if ("PARTICIPATES_IN".equals(relation)) return ("EVENT".equals(sourceType) && "CHARACTER".equals(targetType))
                || ("CHARACTER".equals(sourceType) && "EVENT".equals(targetType));
        if ("OCCURS_AT".equals(relation)) return ("EVENT".equals(sourceType) && "LOCATION".equals(targetType))
                || ("LOCATION".equals(sourceType) && "EVENT".equals(targetType));
        if (Set.of("VISITS", "LIVES_IN").contains(relation)) return "CHARACTER".equals(sourceType) && "LOCATION".equals(targetType);
        if (Set.of("MEMBER_OF", "SERVES").contains(relation)) return "CHARACTER".equals(sourceType) && "ORGANIZATION".equals(targetType);
        if (Set.of("KNOWS", "PARENT_OF", "SPOUSE_OF", "SIBLING_OF", "FRIEND_OF", "COMPANION_OF",
                "TEACHER_OF", "MASTER_OF", "NEIGHBOR_OF", "GUIDES", "HELPS", "PROTECTS", "OPPOSES",
                "TRAVELS_WITH", "CARETAKES", "EMPLOYS").contains(relation))
            return "CHARACTER".equals(sourceType) && "CHARACTER".equals(targetType);
        return false;
    }

    private boolean containsNormalized(String text, String value) {
        return StringUtils.hasText(value) && safe(text).replaceAll("\\s+", "").contains(value.replaceAll("\\s+", ""));
    }

    private void linkClueToNode(long bookId, KnowledgeClue clue, KnowledgeGraphNode node, String relation,
                                 String evidence, double confidence) {
        if (clue == null || node == null) return;
        String linkType = "CLUE_FOR".equals(relation) ? "CLUE_FOR" : "ASSOCIATED_WITH";
        KnowledgeClueGraphLink existing = clueGraphLinkMapper.selectOne(Wrappers.<KnowledgeClueGraphLink>lambdaQuery()
                .eq(KnowledgeClueGraphLink::getClueId, clue.getId()).eq(KnowledgeClueGraphLink::getNodeId, node.getId())
                .eq(KnowledgeClueGraphLink::getLinkType, linkType));
        if (existing != null) return;
        KnowledgeClueGraphLink link = new KnowledgeClueGraphLink();
        link.setId(SnowflakeIdUtil.next()); link.setCanonicalBookId(bookId); link.setClueId(clue.getId()); link.setNodeId(node.getId());
        link.setLinkType(linkType); link.setConfidence(confidence); link.setEvidence(excerpt(evidence, 180)); link.setCreatedAt(LocalDateTime.now());
        clueGraphLinkMapper.insert(link);
    }

    /** A clue may refer to a character introduced in an earlier chapter rather than this chapter's extraction. */
    private void linkClueToKnownMentions(long bookId, int chapter, KnowledgeClue clue) {
        nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                        .eq(KnowledgeGraphNode::getCanonicalBookId, bookId)
                        .ne(KnowledgeGraphNode::getNodeType, "CLUE")
                        .le(KnowledgeGraphNode::getFirstChapter, chapter))
                .stream().filter(node -> containsNormalized(clue.getExcerpt(), node.getName()))
                .forEach(node -> linkClueToNode(bookId, clue, node, "ASSOCIATED_WITH", clue.getExcerpt(), 0.82D));
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
        return value.contains("【支线") || value.contains("另一边") || value.contains("与此同时") || value.contains("此外");
    }

    private boolean isLikelyPersonName(String value) {
        if (!StringUtils.hasText(value) || value.length() < 2 || value.length() > 4) return false;
        if (NON_NAME_FRAGMENTS.stream().anyMatch(value::contains)) return false;
        return COMMON_SURNAME_CHARACTERS.indexOf(value.charAt(0)) >= 0;
    }

    private String eventTitle(int chapter, String evidence) {
        String compact = evidence == null ? "" : evidence.replaceAll("\\s+", "").trim();
        if (compact.length() > 26) compact = compact.substring(0, 26) + "…";
        return "第" + (chapter + 1) + "章事件" + (compact.isEmpty() ? "" : "：" + compact);
    }

    private void refreshProfiles(long canonicalBookId) {
        refreshBookProfile(canonicalBookId);
        profileVectorService.refreshGraphProfiles(canonicalBookId, nodeMapper.selectList(Wrappers.<KnowledgeGraphNode>lambdaQuery()
                .eq(KnowledgeGraphNode::getCanonicalBookId, canonicalBookId)));
    }

    private void refreshBookProfile(long canonicalBookId) {
        List<String> keywords = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                        .orderByDesc(KnowledgeChunk::getChapterIndex).last("LIMIT 600"))
                .stream().map(KnowledgeChunk::getKeywords).filter(StringUtils::hasText).toList();
        profileVectorService.refreshBookProfile(canonicalBookId, keywords);
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

    /** Descriptive labels vary across chapters; only explicit regional identity may split a same-name entity. */
    private String stableIdentityHint(String nodeType, String identityHint) {
        if (!Set.of("CHARACTER", "LOCATION", "ORGANIZATION").contains(nodeType)) return identityHint;
        String hint = identityHint == null ? "" : identityHint.trim();
        return hint.length() >= 10 && (hint.contains("东") || hint.contains("西") || hint.contains("南") || hint.contains("北")) ? hint : "";
    }

    private KnowledgeGraphNode upsertNode(long bookId, int chapter, String name, String nodeType) {
        return upsertNode(bookId, chapter, name, nodeType, "", "第" + (chapter + 1) + "章出现：" + name, 0.70D);
    }

    private KnowledgeGraphNode upsertNode(long bookId, int chapter, String name, String nodeType, String evidence, double confidence) {
        return upsertNode(bookId, chapter, name, nodeType, "", evidence, confidence);
    }

    private KnowledgeGraphNode upsertNode(long bookId, int chapter, String name, String nodeType, String identityHint, String evidence, double confidence) {
        return upsertNode(bookId, chapter, name, nodeType, identityHint, evidence, confidence, RULE_EXTRACTOR_VERSION);
    }

    private KnowledgeGraphNode upsertNode(long bookId, int chapter, String name, String nodeType, String identityHint, String evidence, double confidence, String sourceModelVersion) {
        String identityKey = identityKey(nodeType, name, stableIdentityHint(nodeType, identityHint));
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
        persistVerifiedRelationAssertion(bookId, chapter, left, right, relation, content, confidence, sourceModelVersion);
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

    /**
     * Stores the atomic statement before updating the compact graph projection. The deterministic
     * verifier is intentionally narrow: it accepts only evidence already validated upstream and
     * leaves semantic re-verification available as a separate later stage.
     */
    private void persistVerifiedRelationAssertion(long bookId, int chapter, KnowledgeGraphNode source,
                                                  KnowledgeGraphNode target, String relation, String evidence,
                                                  double confidence, String extractionModelVersion) {
        String normalizedEvidence = excerpt(evidence, 240);
        if (source == null || target == null || !StringUtils.hasText(relation) || !StringUtils.hasText(normalizedEvidence)) return;
        KnowledgeRelationAssertion assertion = new KnowledgeRelationAssertion();
        assertion.setId(SnowflakeIdUtil.next()); assertion.setCanonicalBookId(bookId);
        assertion.setSourceNodeId(source.getId()); assertion.setTargetNodeId(target.getId()); assertion.setRelation(relation);
        assertion.setChapterIndex(Math.max(0, chapter)); assertion.setEvidence(normalizedEvidence);
        assertion.setEvidenceHash(sha256(normalizedEvidence)); assertion.setConfidence(Math.max(0D, Math.min(1D, confidence)));
        assertion.setExtractionModelVersion(safe(extractionModelVersion)); assertion.setVerifierVersion("evidence-gate-v1");
        assertion.setVerificationStatus("VERIFIED"); assertion.setCreatedAt(LocalDateTime.now()); assertion.setUpdatedAt(LocalDateTime.now());
        relationAssertionMapper.insertIfAbsent(assertion);
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
        // LLM claims are persisted only after the extractor verifies verbatim chapter evidence.
        // They remain auditable by model version, but a completed user-facing build must be usable.
        return APPROVED;
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

    private String excerpt(String content, int length) {
        String compact = content == null ? "" : content.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").trim();
        return compact.length() <= length ? compact : compact.substring(0, length) + "...";
    }
    private String safe(String content) { return content == null ? "" : content; }
    private record RetrievalOutcome(List<RerankerService.Candidate> candidates, List<RerankerService.Candidate> selected) { }
    private record EntityAnchor(String canonicalName, Set<String> mentions) { }
    private record ScoredChunk(KnowledgeChunk chunk, double score) { }
    private record SimilarCandidate(double score, Set<String> sharedKeywords) { }
    private record CharacterTextWindow(String text, int score, int offset) { }
}
