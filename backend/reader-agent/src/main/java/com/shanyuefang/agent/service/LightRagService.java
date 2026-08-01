package com.shanyuefang.agent.service;

import com.shanyuefang.agent.domain.vo.CitationVO;

import java.util.List;

/** Budgeted LightRAG context assembled from persisted chapter/arc/book communities. */
public interface LightRagService {
    void refresh(long canonicalBookId);

    /** Incrementally updates the chapter and bounded arc cards for an arriving chapter. */
    default void refreshChapter(long canonicalBookId, int chapterIndex) {
        refresh(canonicalBookId);
    }
    List<String> context(long canonicalBookId, int currentChapter, String query, int maxItems, int maxChars);
    LightRagQuery query(long canonicalBookId, int currentChapter, String query, int maxCommunityItems, int maxCommunityChars);
    void deleteBook(long canonicalBookId);

    /**
     * LightRAG query output: a bounded entity-seeded local graph plus either low-level cards or
     * an explicitly marked escalation to broader arc communities. Full-book cards are excluded.
     */
    record LightRagQuery(List<String> localGraphEdges, List<LightRagCard> cards, boolean escalated) {
        public static LightRagQuery empty() { return new LightRagQuery(List.of(), List.of(), false); }

        public List<String> communities() {
            return cards.stream().map(card -> "[" + card.level() + " Ch. " + (card.chapterStart() + 1) + "-"
                    + (card.chapterEnd() + 1) + "] " + card.summary()
                    + (card.entitySummary().isBlank() || "none".equals(card.entitySummary()) ? "" : " Entities: " + card.entitySummary())).toList();
        }

        public List<CitationVO> citations(long canonicalBookId, int limit) {
            return cards.stream().limit(Math.max(1, limit)).map(card -> new CitationVO(canonicalBookId,
                    card.chapterStart(), excerpt(card.summary(), 180))).toList();
        }

        private static String excerpt(String value, int maxChars) {
            return value.length() <= maxChars ? value : value.substring(0, maxChars) + "...";
        }
    }

    record LightRagCard(String level, int chapterStart, int chapterEnd, String summary, String entitySummary) { }
}
