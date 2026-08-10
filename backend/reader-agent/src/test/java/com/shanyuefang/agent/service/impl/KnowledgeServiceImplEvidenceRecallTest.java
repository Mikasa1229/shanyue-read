package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.entity.KnowledgeChunk;
import com.shanyuefang.agent.domain.entity.KnowledgeEntityAlias;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphNode;
import com.shanyuefang.agent.domain.vo.CitationVO;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.mapper.*;
import com.shanyuefang.agent.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeServiceImplEvidenceRecallTest {
    @BeforeAll
    static void initializeMyBatisLambdaMetadata() {
        initializeTable(KnowledgeChunk.class);
        initializeTable(KnowledgeGraphNode.class);
        initializeTable(KnowledgeEntityAlias.class);
    }

    @Test
    void evidenceRecallKeepsOnlyCurrentWorkAndReadBoundaryThenBuildsChapterCitations() {
        KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
        KnowledgeChunk postgres = new KnowledgeChunk();
        postgres.setCanonicalBookId(99L); postgres.setChapterIndex(2); postgres.setContent("PostgreSQL visible evidence");
        postgres.setKeywords("visible evidence"); postgres.setEmbeddingJson("[1.0]");
        when(chunks.selectList(any())).thenReturn(List.of(postgres));

        VectorKnowledgeStore vectors = mock(VectorKnowledgeStore.class);
        Document visible = new Document("1", "Milvus visible evidence", Map.of("canonicalBookId", 99L, "chapterIndex", 1));
        Document future = new Document("2", "Future spoiler", Map.of("canonicalBookId", 99L, "chapterIndex", 3));
        Document otherBook = new Document("3", "Other work", Map.of("canonicalBookId", 100L, "chapterIndex", 1));
        when(vectors.search(anyString(), anyInt(), anyLong(), anyInt())).thenReturn(List.of(visible, future, otherBook));

        ElasticsearchKnowledgeStore elasticsearch = mock(ElasticsearchKnowledgeStore.class);
        when(elasticsearch.search(99L, 2, "relationship", 6)).thenReturn(List.of(
                new ElasticsearchKnowledgeStore.Hit(2, "Elasticsearch visible evidence")));
        RerankerService reranker = mock(RerankerService.class);
        when(reranker.rerank(anyString(), anyList(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed(anyString())).thenReturn(List.of(1D));
        when(embeddings.similarity(anyList(), anyList())).thenReturn(0.5D);

        KnowledgeServiceImpl service = service(chunks, embeddings, vectors, elasticsearch, reranker);
        List<String> evidence = service.retrieve(99L, 2, "relationship", 3);
        List<CitationVO> citations = service.retrieveCitations(99L, 2, "relationship", 3);

        assertThat(evidence).anyMatch(value -> value.contains("Milvus visible evidence"))
                .noneMatch(value -> value.contains("Future spoiler") || value.contains("Other work"));
        assertThat(citations).allSatisfy(citation -> assertThat(citation.getChapterIndex()).isLessThanOrEqualTo(2));
    }

    @Test
    void pairAnchoringUsesRuntimeEntitiesAndAliasesRatherThanBookSpecificNames() {
        KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
        KnowledgeChunk matching = chunk(4, "城南来客向周青传递密函，周青护送城南来客离开城门。");
        when(chunks.selectList(any())).thenReturn(List.of(matching));

        KnowledgeGraphNode first = character(41L, "林默");
        KnowledgeGraphNode second = character(42L, "周青");
        KnowledgeGraphNodeMapper nodes = mock(KnowledgeGraphNodeMapper.class);
        when(nodes.selectList(any())).thenReturn(List.of(first, second));
        KnowledgeEntityAlias alias = new KnowledgeEntityAlias();
        alias.setNodeId(41L); alias.setAlias("城南来客"); alias.setCanonicalBookId(77L); alias.setFirstChapter(4);
        KnowledgeEntityAliasMapper aliases = mock(KnowledgeEntityAliasMapper.class);
        when(aliases.selectList(any())).thenReturn(List.of(alias));

        VectorKnowledgeStore vectors = mock(VectorKnowledgeStore.class);
        when(vectors.search(anyString(), anyInt(), anyLong(), anyInt())).thenReturn(List.of());
        ElasticsearchKnowledgeStore elasticsearch = mock(ElasticsearchKnowledgeStore.class);
        when(elasticsearch.search(anyLong(), anyInt(), anyString(), anyInt())).thenReturn(List.of());
        RerankerService reranker = mock(RerankerService.class);
        when(reranker.rerank(anyString(), anyList(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed(anyString())).thenReturn(List.of(1D));
        when(embeddings.similarity(anyList(), anyList())).thenReturn(0.5D);

        KnowledgeServiceImpl service = new KnowledgeServiceImpl(mock(KnowledgeDocumentMapper.class), chunks,
                mock(KnowledgeClueMapper.class), mock(KnowledgeVectorProfileMapper.class), nodes, aliases,
                mock(KnowledgeClueGraphLinkMapper.class), mock(LightRagCommunityMapper.class), mock(KnowledgeGraphEdgeMapper.class),
                embeddings, new ObjectMapper(), mock(GraphKnowledgeStore.class), mock(StructuredGraphExtractor.class),
                mock(ProfileVectorService.class), mock(LightRagService.class), vectors, elasticsearch, reranker,
                mock(CanonicalBookFeignClient.class), new AgentProperties());

        KnowledgeService.RetrievalResult result = service.retrieveDetailed(77L, 4, "城南来客和周青发生了什么？", 3, 0L);

        assertThat(result.sourceCandidateCounts()).containsKey("ENTITY_PAIR_ANCHORED");
        assertThat(result.evidence()).anyMatch(value -> value.contains("城南来客") && value.contains("周青"));
    }

    private static void initializeTable(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), type.getName()), type);
        }
    }

    private static KnowledgeChunk chunk(int chapter, String content) {
        KnowledgeChunk value = new KnowledgeChunk();
        value.setCanonicalBookId(77L); value.setChapterIndex(chapter); value.setContent(content);
        value.setKeywords("密函 护送"); value.setEmbeddingJson("[1.0]");
        return value;
    }

    private static KnowledgeGraphNode character(long id, String name) {
        KnowledgeGraphNode value = new KnowledgeGraphNode();
        value.setId(id); value.setCanonicalBookId(77L); value.setName(name); value.setNodeType("CHARACTER");
        value.setFirstChapter(0); value.setReviewStatus("APPROVED");
        return value;
    }

    private KnowledgeServiceImpl service(KnowledgeChunkMapper chunks, EmbeddingService embeddings, VectorKnowledgeStore vectors,
                                         ElasticsearchKnowledgeStore elasticsearch, RerankerService reranker) {
        return new KnowledgeServiceImpl(mock(KnowledgeDocumentMapper.class), chunks, mock(KnowledgeClueMapper.class),
                mock(KnowledgeVectorProfileMapper.class), mock(KnowledgeGraphNodeMapper.class), mock(KnowledgeEntityAliasMapper.class),
                mock(KnowledgeClueGraphLinkMapper.class), mock(LightRagCommunityMapper.class), mock(KnowledgeGraphEdgeMapper.class), embeddings,
                new ObjectMapper(), mock(GraphKnowledgeStore.class), mock(StructuredGraphExtractor.class), mock(ProfileVectorService.class),
                mock(LightRagService.class), vectors, elasticsearch, reranker, mock(CanonicalBookFeignClient.class), new AgentProperties());
    }
}
