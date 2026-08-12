package com.shanyuefang.agent.service.impl;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.entity.KnowledgeVectorProfile;
import com.shanyuefang.agent.domain.vo.UserAgentPreferenceVO;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.agent.mapper.KnowledgeVectorProfileMapper;
import com.shanyuefang.agent.service.AgentPreferenceService;
import com.shanyuefang.agent.service.EmbeddingService;
import com.shanyuefang.agent.service.RecommendationExperimentService;
import com.shanyuefang.agent.service.RecommendationFeedbackService;
import com.shanyuefang.common.result.R;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceImplTest {
    @Test
    void discardsHotCandidatesWithoutCanonicalIdentity() {
        NovelShelfFeignClient shelf = mock(NovelShelfFeignClient.class);
        when(shelf.list(any(), anyLong())).thenReturn(R.ok(List.of()));
        when(shelf.favorites(any(), anyLong())).thenReturn(R.ok(List.of()));
        when(shelf.hot(any(), anyInt())).thenReturn(R.ok(List.of(Map.of("title", "Unmapped hot row", "shelfCount", 10))));
        RecommendationServiceImpl service = service(shelf, mock(CanonicalBookFeignClient.class), profiles());

        List<Map<String, String>> results = service.dynamicShelf(1L);

        assertEquals("先建立你的书架", results.get(0).get("title"));
    }

    @Test
    void dropsIndexedDiscoveryProfileWithoutReadableCanonicalSource() {
        NovelShelfFeignClient shelf = mock(NovelShelfFeignClient.class);
        when(shelf.list(any(), anyLong())).thenReturn(R.ok(List.of()));
        when(shelf.favorites(any(), anyLong())).thenReturn(R.ok(List.of()));
        when(shelf.hot(any(), anyInt())).thenReturn(R.ok(List.of()));
        KnowledgeVectorProfileMapper profiles = profiles();
        KnowledgeVectorProfile profile = new KnowledgeVectorProfile();
        profile.setCanonicalBookId(7L); profile.setContent("mystery voyage");
        when(profiles.selectList(any())).thenReturn(List.of(profile));
        CanonicalBookFeignClient canonical = mock(CanonicalBookFeignClient.class);
        when(canonical.detail(any(), anyLong())).thenReturn(R.ok(Map.of("title", "Orphaned profile")));
        RecommendationServiceImpl service = service(shelf, canonical, profiles);

        List<Map<String, String>> results = service.dynamicShelf(1L);

        assertEquals("先建立你的书架", results.get(0).get("title"));
    }

    private RecommendationServiceImpl service(NovelShelfFeignClient shelf, CanonicalBookFeignClient canonical, KnowledgeVectorProfileMapper profiles) {
        AgentPreferenceService preferences = mock(AgentPreferenceService.class);
        UserAgentPreferenceVO preference = new UserAgentPreferenceVO();
        preference.setPersonalizationEnabled(false); preference.setPreferredGenres(List.of()); preference.setAvoidedThemes(List.of());
        when(preferences.get(anyLong())).thenReturn(preference);
        RecommendationFeedbackService feedback = mock(RecommendationFeedbackService.class);
        when(feedback.feedbackByBook(anyLong())).thenReturn(Map.of());
        RecommendationExperimentService experiments = mock(RecommendationExperimentService.class);
        when(experiments.treatment(anyLong())).thenReturn(false);
        return new RecommendationServiceImpl(shelf, canonical, new AgentProperties(), preferences, feedback, experiments, profiles,
                mock(EmbeddingService.class), new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private KnowledgeVectorProfileMapper profiles() {
        KnowledgeVectorProfileMapper profiles = mock(KnowledgeVectorProfileMapper.class);
        when(profiles.selectOne(any())).thenReturn(null);
        when(profiles.selectList(any())).thenReturn(List.of());
        return profiles;
    }
}
