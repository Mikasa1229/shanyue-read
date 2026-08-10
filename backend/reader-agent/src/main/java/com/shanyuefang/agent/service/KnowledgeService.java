package com.shanyuefang.agent.service;

import com.shanyuefang.agent.domain.dto.IndexChapterDTO;
import com.shanyuefang.agent.domain.vo.CitationVO;
import com.shanyuefang.agent.domain.vo.ClueVO;
import com.shanyuefang.agent.domain.vo.KnowledgeGraphVO;
import com.shanyuefang.agent.domain.vo.SimilarBookVO;
import com.shanyuefang.agent.domain.vo.ReadingMapVO;
import com.shanyuefang.agent.domain.vo.GraphReviewClaimVO;

import java.util.List;
import java.util.Map;

public interface KnowledgeService {
    void indexChapter(IndexChapterDTO dto);
    List<String> retrieve(Long canonicalBookId, Integer currentChapter, String question, int limit);
    List<CitationVO> retrieveCitations(Long canonicalBookId, Integer currentChapter, String question, int limit);
    default RetrievalResult retrieveDetailed(Long canonicalBookId, Integer currentChapter, String question, int limit, long rolloutSubject) {
        List<String> evidence = retrieve(canonicalBookId, currentChapter, question, limit, rolloutSubject);
        return new RetrievalResult(evidence == null ? List.of() : evidence, 0, evidence == null ? 0 : evidence.size(), Map.of());
    }
    default List<String> retrieve(Long canonicalBookId, Integer currentChapter, String question, int limit, long rolloutSubject) {
        return retrieve(canonicalBookId, currentChapter, question, limit);
    }
    default List<CitationVO> retrieveCitations(Long canonicalBookId, Integer currentChapter, String question, int limit, long rolloutSubject) {
        return retrieveCitations(canonicalBookId, currentChapter, question, limit);
    }
    boolean isVisibleCharacter(long canonicalBookId, int currentChapter, String name);
    KnowledgeGraphVO graph(long canonicalBookId, int currentChapter);
    List<ClueVO> clues(long canonicalBookId, int currentChapter);
    List<String> timeline(long canonicalBookId, int currentChapter);
    ReadingMapVO readingMap(long canonicalBookId, int currentChapter);
    List<SimilarBookVO> similarBooks(long canonicalBookId, int currentChapter, int limit);
    void rebuildGraph(long canonicalBookId);
    void rebuildGraph(long canonicalBookId, StructuredGraphExtractor.ModelConfig modelConfig,
                      java.util.function.IntConsumer chapterProgress);
    /** Extracts only the chosen inclusive range and preserves other LightRAG claims. */
    void buildGraphRange(long canonicalBookId, int startChapter, int endChapter,
                         StructuredGraphExtractor.ModelConfig modelConfig,
                         java.util.function.IntConsumer chapterProgress);
    /** Atomically replaces graph claims while retaining durable documents and chunks. */
    void replaceGraphRange(long canonicalBookId, int startChapter, int endChapter,
                           StructuredGraphExtractor.ModelConfig modelConfig,
                           java.util.function.IntConsumer chapterProgress);
    /** Recomputes story events and clues without resending chapter text for entity extraction. */
    void rebuildDerivedInsights(long canonicalBookId, int startChapter, int endChapter,
                                StructuredGraphExtractor.ModelConfig modelConfig);
    void rebuildCharacterKnowledge(long canonicalBookId, int startChapter, int endChapter,
                                   StructuredGraphExtractor.ModelConfig modelConfig);
    void clearGraph(long canonicalBookId);

    /** Repairs optional evidence projections without re-extracting or rewriting graph claims. */
    void reprojectEvidence(long canonicalBookId, int maxChunks);

    /** Rebuilds only the optional Neo4j projection from PostgreSQL's authoritative graph tables. */
    void reprojectGraph(long canonicalBookId);
    List<GraphReviewClaimVO> graphReviewClaims(long canonicalBookId, int limit);
    void reviewGraphClaim(long canonicalBookId, String claimType, long claimId, String reviewStatus);
    void deleteBookKnowledge(long canonicalBookId);

    /** Candidate counts and source contribution are safe to persist; candidate text is not. */
    record RetrievalResult(List<String> evidence, int candidateCount, int selectedCount,
                           Map<String, Integer> sourceCandidateCounts) {
        public RetrievalResult {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            sourceCandidateCounts = sourceCandidateCounts == null ? Map.of() : Map.copyOf(sourceCandidateCounts);
        }
    }
}
