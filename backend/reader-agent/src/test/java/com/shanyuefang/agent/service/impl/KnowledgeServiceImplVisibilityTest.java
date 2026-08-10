package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphNode;
import com.shanyuefang.agent.domain.entity.KnowledgeEntityAlias;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.mapper.KnowledgeChunkMapper;
import com.shanyuefang.agent.mapper.KnowledgeClueGraphLinkMapper;
import com.shanyuefang.agent.mapper.KnowledgeClueMapper;
import com.shanyuefang.agent.mapper.KnowledgeDocumentMapper;
import com.shanyuefang.agent.mapper.KnowledgeEntityAliasMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphEdgeMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphNodeMapper;
import com.shanyuefang.agent.mapper.KnowledgeVectorProfileMapper;
import com.shanyuefang.agent.mapper.LightRagCommunityMapper;
import com.shanyuefang.agent.service.EmbeddingService;
import com.shanyuefang.agent.service.GraphKnowledgeStore;
import com.shanyuefang.agent.service.LightRagService;
import com.shanyuefang.agent.service.ProfileVectorService;
import com.shanyuefang.agent.service.StructuredGraphExtractor;
import com.shanyuefang.agent.service.VectorKnowledgeStore;
import com.shanyuefang.agent.service.ElasticsearchKnowledgeStore;
import com.shanyuefang.agent.service.RerankerService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeServiceImplVisibilityTest {
    @BeforeAll
    static void initializeMyBatisLambdaMetadata() {
        if (TableInfoHelper.getTableInfo(KnowledgeGraphNode.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), KnowledgeGraphNode.class.getName()), KnowledgeGraphNode.class);
        }
        if (TableInfoHelper.getTableInfo(KnowledgeEntityAlias.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), KnowledgeEntityAlias.class.getName()), KnowledgeEntityAlias.class);
        }
    }

    @Test
    void characterInterviewVisibilityRequiresApprovedAndConfidentGraphEvidence() {
        KnowledgeGraphNodeMapper nodes = mock(KnowledgeGraphNodeMapper.class);
        when(nodes.selectCount(any(Wrapper.class))).thenReturn(1L);
        AgentProperties properties = new AgentProperties();
        properties.setMinGraphConfidence(0.75D);
        KnowledgeServiceImpl service = service(nodes, properties);

        assertThat(service.isVisibleCharacter(8L, 3, "林默")).isTrue();

        ArgumentCaptor<Wrapper<KnowledgeGraphNode>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(nodes).selectCount(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("review_status", "confidence");
        assertThat(((AbstractWrapper<?, ?, ?>) query.getValue()).getParamNameValuePairs())
                .containsValue("APPROVED").containsValue(0.75D);
    }

    @Test
    void characterInterviewAcceptsOnlyAnUnambiguousVisibleAlias() {
        KnowledgeGraphNodeMapper nodes = mock(KnowledgeGraphNodeMapper.class);
        KnowledgeEntityAliasMapper aliases = mock(KnowledgeEntityAliasMapper.class);
        when(nodes.selectCount(any(Wrapper.class))).thenReturn(0L);
        KnowledgeEntityAlias alias = new KnowledgeEntityAlias(); alias.setNodeId(71L);
        when(aliases.selectList(any(Wrapper.class))).thenReturn(List.of(alias));
        KnowledgeGraphNode character = new KnowledgeGraphNode();
        character.setId(71L); character.setCanonicalBookId(8L); character.setNodeType("CHARACTER");
        character.setFirstChapter(2); character.setReviewStatus("APPROVED"); character.setConfidence(0.80D);
        when(nodes.selectById(71L)).thenReturn(character);
        AgentProperties properties = new AgentProperties(); properties.setMinGraphConfidence(0.75D);

        assertThat(service(nodes, aliases, properties).isVisibleCharacter(8L, 3, "Captain")).isTrue();
    }

    @Test
    void characterInterviewRefusesAmbiguousAliases() {
        KnowledgeGraphNodeMapper nodes = mock(KnowledgeGraphNodeMapper.class);
        KnowledgeEntityAliasMapper aliases = mock(KnowledgeEntityAliasMapper.class);
        when(nodes.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(aliases.selectList(any(Wrapper.class))).thenReturn(List.of(new KnowledgeEntityAlias(), new KnowledgeEntityAlias()));

        assertThat(service(nodes, aliases, new AgentProperties()).isVisibleCharacter(8L, 3, "Captain")).isFalse();
        verify(nodes, never()).selectById(any());
    }

    @Test
    void replacementKeepsExternalProjectionsUntilRelationalBuildSucceeds() {
        GraphKnowledgeStore graph = mock(GraphKnowledgeStore.class);
        ProfileVectorService profiles = mock(ProfileVectorService.class);
        KnowledgeGraphNodeMapper nodes = mock(KnowledgeGraphNodeMapper.class);
        when(nodes.selectList(any(Wrapper.class))).thenReturn(List.of());
        KnowledgeServiceImpl service = spy(service(nodes, mock(KnowledgeEntityAliasMapper.class),
                new AgentProperties(), graph, profiles));
        StructuredGraphExtractor.ModelConfig model = new StructuredGraphExtractor.ModelConfig(
                "openai", "test-model", "https://example.test", "test-key");
        doNothing().when(service).buildGraphRange(any(Long.class), any(Integer.class), any(Integer.class), any(), any());

        service.replaceGraphRange(8L, 1, 100, model, ignored -> { });

        verify(graph, never()).deleteBook(any(Long.class));
        InOrder projectionRefresh = inOrder(service, profiles);
        projectionRefresh.verify(service).buildGraphRange(any(Long.class), any(Integer.class), any(Integer.class), any(), any());
        projectionRefresh.verify(profiles).deleteBookProfiles(8L);
        projectionRefresh.verify(profiles).refreshBookProfile(any(Long.class), any());
        projectionRefresh.verify(profiles).refreshGraphProfiles(any(Long.class), any());
    }

    @Test
    void replacementFailureDoesNotDeleteLastReadableExternalProjection() {
        GraphKnowledgeStore graph = mock(GraphKnowledgeStore.class);
        ProfileVectorService profiles = mock(ProfileVectorService.class);
        KnowledgeServiceImpl service = spy(service(mock(KnowledgeGraphNodeMapper.class),
                mock(KnowledgeEntityAliasMapper.class), new AgentProperties(), graph, profiles));
        StructuredGraphExtractor.ModelConfig model = new StructuredGraphExtractor.ModelConfig(
                "openai", "test-model", "https://example.test", "test-key");
        doThrow(new IllegalStateException("model unavailable")).when(service)
                .buildGraphRange(any(Long.class), any(Integer.class), any(Integer.class), any(), any());

        assertThrows(IllegalStateException.class,
                () -> service.replaceGraphRange(8L, 1, 100, model, ignored -> { }));

        verify(graph, never()).deleteBook(any(Long.class));
        verify(profiles, never()).deleteBookProfiles(any(Long.class));
        verify(profiles, never()).refreshGraphProfiles(any(Long.class), any());
    }

    private KnowledgeServiceImpl service(KnowledgeGraphNodeMapper nodes, AgentProperties properties) {
        return service(nodes, mock(KnowledgeEntityAliasMapper.class), properties);
    }

    private KnowledgeServiceImpl service(KnowledgeGraphNodeMapper nodes, KnowledgeEntityAliasMapper aliases, AgentProperties properties) {
        return service(nodes, aliases, properties, mock(GraphKnowledgeStore.class), mock(ProfileVectorService.class));
    }

    private KnowledgeServiceImpl service(KnowledgeGraphNodeMapper nodes, KnowledgeEntityAliasMapper aliases,
                                         AgentProperties properties, GraphKnowledgeStore graph,
                                         ProfileVectorService profiles) {
        return new KnowledgeServiceImpl(
                mock(KnowledgeDocumentMapper.class), mock(KnowledgeChunkMapper.class), mock(KnowledgeClueMapper.class),
                mock(KnowledgeVectorProfileMapper.class), nodes, aliases,
                mock(KnowledgeClueGraphLinkMapper.class), mock(LightRagCommunityMapper.class), mock(KnowledgeGraphEdgeMapper.class),
                mock(com.shanyuefang.agent.mapper.KnowledgeRelationAssertionMapper.class), mock(EmbeddingService.class),
                new ObjectMapper(), graph, mock(StructuredGraphExtractor.class), profiles,
                mock(LightRagService.class), mock(VectorKnowledgeStore.class), mock(ElasticsearchKnowledgeStore.class),
                mock(RerankerService.class), mock(CanonicalBookFeignClient.class), properties);
    }
}
