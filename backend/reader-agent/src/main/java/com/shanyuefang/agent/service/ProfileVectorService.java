package com.shanyuefang.agent.service;

import com.shanyuefang.agent.domain.entity.KnowledgeGraphNode;
import com.shanyuefang.agent.domain.entity.LightRagCommunity;

import java.util.List;

/** Maintains book, character, event, and privacy-scoped user preference vector profiles. */
public interface ProfileVectorService {
    void refreshBookProfile(long canonicalBookId, List<String> indexedKeywords);
    void refreshGraphProfiles(long canonicalBookId, List<KnowledgeGraphNode> nodes);
    void refreshCommunityProfiles(long canonicalBookId, List<LightRagCommunity> communities);
    void refreshUserPreference(long userId, List<String> preferredGenres, List<String> avoidedThemes, boolean enabled);
    void deleteBookProfiles(long canonicalBookId);
    void deleteUserPreference(long userId);
}
