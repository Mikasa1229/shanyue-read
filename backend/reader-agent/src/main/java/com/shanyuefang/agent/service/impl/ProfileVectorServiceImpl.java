package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphNode;
import com.shanyuefang.agent.domain.entity.KnowledgeVectorProfile;
import com.shanyuefang.agent.domain.entity.LightRagCommunity;
import com.shanyuefang.agent.mapper.KnowledgeVectorProfileMapper;
import com.shanyuefang.agent.service.EmbeddingService;
import com.shanyuefang.agent.service.ProfileVectorService;
import com.shanyuefang.agent.service.ProfileVectorStore;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileVectorServiceImpl implements ProfileVectorService {
    private final KnowledgeVectorProfileMapper profileMapper;
    private final AgentProperties properties;
    private final EmbeddingService embeddingService;
    private final ProfileVectorStore profileVectorStore;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshBookProfile(long canonicalBookId, List<String> indexedKeywords) {
        String content = "Indexed book profile: " + String.join(" ", indexedKeywords.stream().distinct().limit(160).toList());
        upsert("BOOK", canonicalBookId, canonicalBookId, content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshGraphProfiles(long canonicalBookId, List<KnowledgeGraphNode> nodes) {
        for (KnowledgeGraphNode node : nodes) {
            String type = "EVENT".equals(node.getNodeType()) ? "EVENT" : "CHARACTER";
            if (!"EVENT".equals(node.getNodeType()) && !"CHARACTER".equals(node.getNodeType())) continue;
            if (!"APPROVED".equals(node.getReviewStatus()) || node.getConfidence() == null
                    || node.getConfidence() < properties.getMinGraphConfidence()) {
                tombstoneProfile(type, node.getId());
                continue;
            }
            String content = type.toLowerCase(Locale.ROOT) + ": " + node.getName() + ". " + safe(node.getEvidence());
            upsert(type, node.getId(), canonicalBookId, content);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshCommunityProfiles(long canonicalBookId, List<LightRagCommunity> communities) {
        List<LightRagCommunity> activeCommunities = communities == null ? List.of() : communities;
        Set<Long> activeIds = activeCommunities.stream().map(LightRagCommunity::getId).collect(Collectors.toSet());
        // Graph communities are rebuilt as connected components. Their IDs can legitimately change,
        // so remove obsolete Milvus/PostgreSQL projections instead of letting old graph cards leak
        // into subsequent similarity retrieval.
        List<KnowledgeVectorProfile> existingProfiles = profileMapper.selectList(Wrappers.<KnowledgeVectorProfile>lambdaQuery()
                        .eq(KnowledgeVectorProfile::getProfileType, "COMMUNITY")
                        .eq(KnowledgeVectorProfile::getCanonicalBookId, canonicalBookId)
                        .isNull(KnowledgeVectorProfile::getDeletedAt));
        (existingProfiles == null ? List.<KnowledgeVectorProfile>of() : existingProfiles).stream()
                .filter(profile -> !activeIds.contains(profile.getSubjectId())).forEach(this::tombstone);
        for (LightRagCommunity community : activeCommunities) {
            String content = "LightRAG " + community.getCommunityLevel() + " community: " + safe(community.getSummary()) + " " + safe(community.getEntitySummary());
            upsert("COMMUNITY", community.getId(), canonicalBookId, content);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshUserPreference(long userId, List<String> preferredGenres, List<String> avoidedThemes, boolean enabled) {
        if (!enabled) {
            deleteUserPreference(userId);
            return;
        }
        String content = "Preferred genres: " + String.join(", ", safeList(preferredGenres))
                + ". Avoided themes: " + String.join(", ", safeList(avoidedThemes));
        upsert("USER_PREFERENCE", userId, null, content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBookProfiles(long canonicalBookId) {
        List<KnowledgeVectorProfile> profiles = profileMapper.selectList(Wrappers.<KnowledgeVectorProfile>lambdaQuery()
                .eq(KnowledgeVectorProfile::getCanonicalBookId, canonicalBookId)
                .isNull(KnowledgeVectorProfile::getDeletedAt));
        for (KnowledgeVectorProfile profile : profiles) tombstone(profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserPreference(long userId) {
        KnowledgeVectorProfile profile = profileMapper.selectOne(Wrappers.<KnowledgeVectorProfile>lambdaQuery()
                .eq(KnowledgeVectorProfile::getProfileType, "USER_PREFERENCE")
                .eq(KnowledgeVectorProfile::getSubjectId, userId));
        if (profile != null && profile.getDeletedAt() == null) tombstone(profile);
    }

    private void upsert(String type, long subjectId, Long canonicalBookId, String content) {
        String hash = sha256(content);
        KnowledgeVectorProfile profile = profileMapper.selectOne(Wrappers.<KnowledgeVectorProfile>lambdaQuery()
                .eq(KnowledgeVectorProfile::getProfileType, type).eq(KnowledgeVectorProfile::getSubjectId, subjectId));
        if (profile != null && hash.equals(profile.getContentHash()) && embeddingVersion().equals(profile.getModelVersion()) && profile.getDeletedAt() == null) return;
        if (profile == null) {
            profile = new KnowledgeVectorProfile();
            profile.setId(SnowflakeIdUtil.next()); profile.setProfileType(type); profile.setSubjectId(subjectId);
        }
        profile.setCanonicalBookId(canonicalBookId); profile.setContent(content); profile.setContentHash(hash);
        profile.setEmbeddingJson(writeVector(embeddingService.embed(content))); profile.setModelVersion(embeddingVersion());
        profile.setIndexedAt(LocalDateTime.now()); profile.setDeletedAt(null);
        if (profileMapper.selectById(profile.getId()) == null) profileMapper.insert(profile); else profileMapper.updateById(profile);
        profileVectorStore.upsert(profile);
    }

    private void tombstone(KnowledgeVectorProfile profile) {
        profile.setDeletedAt(LocalDateTime.now()); profileMapper.updateById(profile);
        profileVectorStore.delete(profile.getProfileType(), profile.getSubjectId());
    }

    private void tombstoneProfile(String type, long subjectId) {
        KnowledgeVectorProfile profile = profileMapper.selectOne(Wrappers.<KnowledgeVectorProfile>lambdaQuery()
                .eq(KnowledgeVectorProfile::getProfileType, type)
                .eq(KnowledgeVectorProfile::getSubjectId, subjectId)
                .isNull(KnowledgeVectorProfile::getDeletedAt));
        if (profile != null) tombstone(profile);
    }

    private String writeVector(List<Double> vector) { try { return objectMapper.writeValueAsString(vector); } catch (Exception e) { throw new IllegalStateException("Could not serialize profile embedding", e); } }
    private String sha256(String content) { try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)); StringBuilder value = new StringBuilder(); for (byte item : digest) value.append(String.format(Locale.ROOT, "%02x", item)); return value.toString(); } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); } }
    private List<String> safeList(List<String> values) { return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).toList(); }
    private String safe(String value) { return value == null ? "" : value; }
    private String embeddingVersion() { return properties.getEmbeddingModelVersion() == null || properties.getEmbeddingModelVersion().isBlank() ? "unknown" : properties.getEmbeddingModelVersion().trim(); }
}
