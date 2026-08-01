package com.shanyuefang.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.entity.KnowledgeVectorProfile;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphNode;
import com.shanyuefang.agent.domain.entity.LightRagCommunity;
import com.shanyuefang.agent.mapper.KnowledgeVectorProfileMapper;
import com.shanyuefang.agent.service.EmbeddingService;
import com.shanyuefang.agent.service.ProfileVectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ProfileVectorServiceImplTest {
    @Test
    void reprojectsUnchangedProfileWhenEmbeddingVersionChanges() {
        KnowledgeVectorProfileMapper mapper = mock(KnowledgeVectorProfileMapper.class);
        KnowledgeVectorProfile existing = new KnowledgeVectorProfile();
        existing.setId(9L);
        existing.setProfileType("BOOK");
        existing.setSubjectId(7L);
        existing.setCanonicalBookId(7L);
        // This is the unchanged content hash for "Indexed book profile: same keywords".
        existing.setContentHash("570252f10658ff0771520043d31fcc5c51bc03bec6f9f7a3903fdd66b95f502a");
        existing.setModelVersion("embedding-v1");
        when(mapper.selectOne(any())).thenReturn(existing);
        when(mapper.selectById(9L)).thenReturn(existing);
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed(any())).thenReturn(List.of(0.1d, 0.2d));
        AgentProperties properties = new AgentProperties();
        properties.setEmbeddingModelVersion("embedding-v2");
        ProfileVectorStore store = mock(ProfileVectorStore.class);

        new ProfileVectorServiceImpl(mapper, properties, embeddings, store, new ObjectMapper())
                .refreshBookProfile(7L, List.of("same", "keywords"));

        assertEquals("embedding-v2", existing.getModelVersion());
        verify(mapper).updateById(existing);
        verify(store).upsert(existing);
    }

    @Test
    void unapprovedGraphClaimsAreRemovedFromCharacterVectorProjection() {
        KnowledgeVectorProfileMapper mapper = mock(KnowledgeVectorProfileMapper.class);
        KnowledgeVectorProfile profile = new KnowledgeVectorProfile();
        profile.setId(12L); profile.setProfileType("CHARACTER"); profile.setSubjectId(55L);
        when(mapper.selectOne(any())).thenReturn(profile);
        ProfileVectorStore store = mock(ProfileVectorStore.class);
        KnowledgeGraphNode pending = new KnowledgeGraphNode();
        pending.setId(55L); pending.setNodeType("CHARACTER"); pending.setReviewStatus("PENDING"); pending.setConfidence(0.99D);

        new ProfileVectorServiceImpl(mapper, new AgentProperties(), mock(EmbeddingService.class), store, new ObjectMapper())
                .refreshGraphProfiles(7L, List.of(pending));

        verify(mapper).updateById(profile);
        verify(store).delete("CHARACTER", 55L);
        verify(store, never()).upsert(any());
    }

    @Test
    void removesStaleCommunityProfilesAfterGraphCommunityRebuild() {
        KnowledgeVectorProfileMapper mapper = mock(KnowledgeVectorProfileMapper.class);
        KnowledgeVectorProfile stale = new KnowledgeVectorProfile();
        stale.setId(15L); stale.setProfileType("COMMUNITY"); stale.setSubjectId(44L); stale.setCanonicalBookId(7L);
        when(mapper.selectList(any())).thenReturn(List.of(stale));
        ProfileVectorStore store = mock(ProfileVectorStore.class);
        LightRagCommunity current = new LightRagCommunity();
        current.setId(45L); current.setCommunityLevel("GRAPH"); current.setSummary("verified"); current.setEntitySummary("A");
        when(mapper.selectOne(any())).thenReturn(null);
        EmbeddingService embeddings = mock(EmbeddingService.class);
        when(embeddings.embed(any())).thenReturn(List.of(0.1D));

        new ProfileVectorServiceImpl(mapper, new AgentProperties(), embeddings, store, new ObjectMapper())
                .refreshCommunityProfiles(7L, List.of(current));

        verify(mapper).updateById(stale);
        verify(store).delete("COMMUNITY", 44L);
    }

}
