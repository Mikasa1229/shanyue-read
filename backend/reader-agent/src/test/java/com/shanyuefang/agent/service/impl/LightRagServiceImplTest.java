package com.shanyuefang.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphNode;
import com.shanyuefang.agent.domain.entity.LightRagCommunity;
import com.shanyuefang.agent.mapper.KnowledgeChunkMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphEdgeMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphNodeMapper;
import com.shanyuefang.agent.mapper.LightRagCommunityMapper;
import com.shanyuefang.agent.service.EmbeddingService;
import com.shanyuefang.agent.service.GraphKnowledgeStore;
import com.shanyuefang.agent.service.LightRagService;
import com.shanyuefang.agent.service.ProfileVectorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LightRagServiceImplTest {
    @Test
    void usesEntitySeededLocalGraphBeforeItEverEscalatesToArcCards() {
        LightRagCommunity chapter = card("CHAPTER", "local chapter evidence");
        LightRagCommunity arc = card("ARC", "broader arc summary");
        KnowledgeGraphNode hero = new KnowledgeGraphNode();
        hero.setName("沈青"); hero.setConfidence(0.9D); hero.setReviewStatus("APPROVED"); hero.setFirstChapter(0);
        LightRagCommunityMapper communities = mock(LightRagCommunityMapper.class);
        when(communities.selectList(any())).thenReturn(List.of(chapter, arc));
        KnowledgeGraphNodeMapper nodes = mock(KnowledgeGraphNodeMapper.class);
        when(nodes.selectList(any())).thenReturn(List.of(hero));
        GraphKnowledgeStore graph = mock(GraphKnowledgeStore.class);
        when(graph.localNeighborhood(7L, 3, List.of("沈青"), 36)).thenReturn(List.of("沈青 -同伴-> 林月 (Ch. 2)"));
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed(any())).thenReturn(List.of(1D));
        when(embeddings.similarity(any(), any())).thenReturn(1D);

        LightRagService.LightRagQuery result = service(communities, nodes, graph, embeddings).query(7L, 3, "沈青和谁同行", 3, 1200);

        assertFalse(result.escalated());
        assertTrue(result.localGraphEdges().stream().anyMatch(value -> value.contains("同伴")));
        assertTrue(result.communities().stream().allMatch(value -> value.contains("【章节卡片") || value.contains("【关系社区")));
    }

    @Test
    void escalatesOnlyToArcAndNeverUsesBookCardsWhenLocalEvidenceIsAbsent() {
        LightRagCommunity book = card("BOOK", "whole book catalogue card");
        LightRagCommunity safeBook = card("BOOK_SAFE", "safe whole book catalogue card");
        LightRagCommunity arc = card("ARC", "bounded arc evidence");
        LightRagCommunityMapper communities = mock(LightRagCommunityMapper.class);
        when(communities.selectList(any())).thenReturn(List.of(book, safeBook, arc));
        KnowledgeGraphNodeMapper nodes = mock(KnowledgeGraphNodeMapper.class);
        when(nodes.selectList(any())).thenReturn(List.of());
        GraphKnowledgeStore graph = mock(GraphKnowledgeStore.class);
        when(graph.localNeighborhood(7L, 3, List.of(), 36)).thenReturn(List.of());
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed(any())).thenReturn(List.of(1D));
        when(embeddings.similarity(any(), any())).thenReturn(1D);

        LightRagService.LightRagQuery result = service(communities, nodes, graph, embeddings)
                .query(7L, 3, "unknown theme", 3, 1200);

        assertTrue(result.escalated());
        assertTrue(result.communities().stream().allMatch(value -> value.contains("【章节片段")));
        assertFalse(result.communities().stream().anyMatch(value -> value.contains("BOOK")));
    }

    private LightRagServiceImpl service(LightRagCommunityMapper communities, KnowledgeGraphNodeMapper nodes,
                                         GraphKnowledgeStore graph, EmbeddingService embeddings) {
        return new LightRagServiceImpl(communities, mock(KnowledgeChunkMapper.class), nodes, mock(KnowledgeGraphEdgeMapper.class),
                embeddings, new ObjectMapper(), mock(ProfileVectorService.class), new AgentProperties(), graph);
    }

    private LightRagCommunity card(String level, String summary) {
        LightRagCommunity card = new LightRagCommunity();
        card.setCommunityLevel(level); card.setChapterStart(0); card.setChapterEnd(2); card.setSummary(summary);
        card.setEntitySummary("沈青"); card.setEmbeddingJson("[1.0]");
        return card;
    }
}
