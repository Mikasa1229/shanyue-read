package com.shanyuefang.agent.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KnowledgeChunkingTest {
    @Test
    void preservesSentenceBoundariesAndCarriesBoundedOverlap() {
        String first = "甲在城门前等待，听见远处钟声。".repeat(35);
        String second = "乙随后抵达，两人约定次日同行。".repeat(35);

        List<String> chunks = KnowledgeServiceImpl.chunks(first + second);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(800));
        assertThat(chunks.get(0)).endsWith("。");
        assertThat(chunks.get(1)).contains(chunks.get(0).substring(chunks.get(0).length() - 120));
    }

    @Test
    void safelySplitsOneSentenceThatExceedsTheTarget() {
        List<String> chunks = KnowledgeServiceImpl.chunks("甲".repeat(1700));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(800));
    }

    @Test
    void characterScenesKeepSentenceBoundariesInsteadOfSplittingActionEvidence() throws Exception {
        KnowledgeServiceImpl service = service();
        java.lang.reflect.Method method = KnowledgeServiceImpl.class.getDeclaredMethod("characterScenes", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> scenes = (List<String>) method.invoke(service,
                "林默扶住周青。周青将密函交给林默。" + "甲".repeat(710) + "。黎青带林默离开港口。");

        assertThat(scenes).allMatch(scene -> scene.endsWith("。"));
        assertThat(scenes).anyMatch(scene -> scene.contains("林默扶住周青。周青将密函交给林默。"));
    }

    private KnowledgeServiceImpl service() {
        return new KnowledgeServiceImpl(mock(com.shanyuefang.agent.mapper.KnowledgeDocumentMapper.class),
                mock(com.shanyuefang.agent.mapper.KnowledgeChunkMapper.class), mock(com.shanyuefang.agent.mapper.KnowledgeClueMapper.class),
                mock(com.shanyuefang.agent.mapper.KnowledgeClueResolutionMapper.class),
                mock(com.shanyuefang.agent.mapper.KnowledgeVectorProfileMapper.class), mock(com.shanyuefang.agent.mapper.KnowledgeGraphNodeMapper.class),
                mock(com.shanyuefang.agent.mapper.KnowledgeEntityAliasMapper.class), mock(com.shanyuefang.agent.mapper.KnowledgeClueGraphLinkMapper.class),
                mock(com.shanyuefang.agent.mapper.LightRagCommunityMapper.class), mock(com.shanyuefang.agent.mapper.KnowledgeGraphEdgeMapper.class),
                mock(com.shanyuefang.agent.mapper.KnowledgeRelationAssertionMapper.class), mock(com.shanyuefang.agent.service.EmbeddingService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(), mock(com.shanyuefang.agent.service.GraphKnowledgeStore.class),
                mock(com.shanyuefang.agent.service.StructuredGraphExtractor.class), mock(com.shanyuefang.agent.service.ProfileVectorService.class),
                mock(com.shanyuefang.agent.service.LightRagService.class), mock(com.shanyuefang.agent.service.VectorKnowledgeStore.class),
                mock(com.shanyuefang.agent.service.ElasticsearchKnowledgeStore.class), mock(com.shanyuefang.agent.service.RerankerService.class),
                mock(com.shanyuefang.agent.feign.CanonicalBookFeignClient.class), new com.shanyuefang.agent.config.AgentProperties());
    }
}
