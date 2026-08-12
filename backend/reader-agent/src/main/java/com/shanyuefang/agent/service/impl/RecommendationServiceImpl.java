package com.shanyuefang.agent.service.impl;

import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.service.RecommendationService;
import com.shanyuefang.agent.service.AgentPreferenceService;
import com.shanyuefang.agent.service.RecommendationFeedbackService;
import com.shanyuefang.agent.service.RecommendationExperimentService;
import com.shanyuefang.agent.domain.vo.UserAgentPreferenceVO;
import com.shanyuefang.agent.domain.entity.KnowledgeVectorProfile;
import com.shanyuefang.agent.mapper.KnowledgeVectorProfileMapper;
import com.shanyuefang.agent.service.EmbeddingService;
import com.shanyuefang.agent.domain.vo.ReadingPlanVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {
    private final NovelShelfFeignClient shelfClient;
    private final CanonicalBookFeignClient canonicalBookClient;
    private final AgentProperties agentProperties;
    private final AgentPreferenceService preferenceService;
    private final RecommendationFeedbackService feedbackService;
    private final RecommendationExperimentService experimentService;
    private final KnowledgeVectorProfileMapper vectorProfileMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    @Override
    public List<Map<String, String>> dynamicShelf(long userId) {
        UserAgentPreferenceVO preference = preferenceService.get(userId);
        boolean personalize = Boolean.TRUE.equals(preference.getPersonalizationEnabled());
        List<String> preferredGenres = normalize(preference.getPreferredGenres());
        List<String> avoidedThemes = normalize(preference.getAvoidedThemes());
        Map<Long, String> feedback = feedbackService.feedbackByBook(userId);
        KnowledgeVectorProfile userVector = vectorProfileMapper.selectOne(Wrappers.<KnowledgeVectorProfile>lambdaQuery()
                .eq(KnowledgeVectorProfile::getProfileType, "USER_PREFERENCE")
                .eq(KnowledgeVectorProfile::getSubjectId, userId).isNull(KnowledgeVectorProfile::getDeletedAt));
        try {
            R<List<Map<String, Object>>> response;
            try {
                response = shelfClient.list(agentProperties.getInternalToken(), userId);
            } catch (Exception ignored) {
                // Discovery remains available even while the user's shelf projection is briefly unavailable.
                response = null;
            }
            List<Map<String, Object>> shelfBooks = response == null || response.getData() == null ? List.of() : response.getData();
            Set<Long> favoriteBookIds;
            try {
                R<List<Long>> favorites = shelfClient.favorites(agentProperties.getInternalToken(), userId);
                favoriteBookIds = new HashSet<>(favorites == null || favorites.getData() == null ? List.of() : favorites.getData());
            } catch (Exception ignored) { favoriteBookIds = Set.of(); }
            List<Candidate> candidates = new ArrayList<>();
            Set<Long> shelfBookIds = new HashSet<>();
            for (Map<String, Object> book : shelfBooks) {
                String title = String.valueOf(book.getOrDefault("bookName", "Untitled work"));
                Object progress = book.get("lastChapterName");
                Long canonicalBookId = parseBookId(book.get("canonicalBookId"));
                if (canonicalBookId != null) shelfBookIds.add(canonicalBookId);
                if (isExcluded(feedback.get(canonicalBookId))) continue;
                String metadata = book.values().stream().filter(value -> value != null)
                        .map(value -> String.valueOf(value).toLowerCase(Locale.ROOT)).reduce("", (left, right) -> left + " " + right);
                if (personalize && containsAny(metadata, avoidedThemes)) continue;
                List<String> matches = preferredGenres.stream().filter(metadata::contains).toList();
                String reason = progress == null ? "Start reading to unlock a spoiler-safe recap."
                        : "Continue from " + progress;
                if (personalize && !matches.isEmpty()) reason += " 符合你的偏好：" + String.join("、", matches) + "。";
                else if (personalize && !preferredGenres.isEmpty()) reason += " Ranked using your saved reading preferences.";
                int feedbackBoost = feedbackBoost(canonicalBookId == null ? null : feedback.get(canonicalBookId));
                double vectorScore = canonicalBookId == null || userVector == null ? 0d : preferenceSimilarity(userVector, canonicalBookId);
                if (personalize && vectorScore > 0.20d) reason += " 与你的阅读偏好特征相近。";
                candidates.add(new Candidate(title, reason, canonicalBookId, matches.size() + feedbackBoost
                        + (canonicalBookId != null && favoriteBookIds.contains(canonicalBookId) ? 2 : 0), progress == null ? 0 : 1, vectorScore));
            }
            // Shelf continuation is useful, but an Agent should also surface indexed works the user has not met yet.
            // The PostgreSQL profile table remains the durable fallback when Milvus is intentionally unavailable.
            Map<Long, Map<String, Object>> details = new HashMap<>();
            List<KnowledgeVectorProfile> discoverable = vectorProfileMapper.selectList(Wrappers.<KnowledgeVectorProfile>lambdaQuery()
                    .eq(KnowledgeVectorProfile::getProfileType, "BOOK")
                    .isNull(KnowledgeVectorProfile::getDeletedAt)
                    .orderByDesc(KnowledgeVectorProfile::getIndexedAt)
                    .last("LIMIT 80"));
            for (KnowledgeVectorProfile profile : discoverable) {
                Long canonicalBookId = profile.getCanonicalBookId();
                if (canonicalBookId == null || shelfBookIds.contains(canonicalBookId) || isExcluded(feedback.get(canonicalBookId))) continue;
                String metadata = (profile.getContent() == null ? "" : profile.getContent()).toLowerCase(Locale.ROOT);
                if (personalize && containsAny(metadata, avoidedThemes)) continue;
                List<String> matches = preferredGenres.stream().filter(metadata::contains).toList();
                double vectorScore = personalize && userVector != null ? preferenceSimilarity(userVector, canonicalBookId) : 0d;
                Map<String, Object> detail = details.computeIfAbsent(canonicalBookId, this::canonicalDetail);
                // A discovery card must resolve to an actual source, not merely an orphaned vector profile.
                if (!hasReadableSource(detail)) continue;
                String title = String.valueOf(detail.getOrDefault("title", "已索引作品 #" + canonicalBookId));
                String reason = vectorScore > 0.20d ? "与您的阅读偏好特征相近。"
                        : !matches.isEmpty() ? "符合您的偏好：" + String.join("、", matches) + "。"
                        : "一部尚未加入您书架的已索引作品。";
                candidates.add(new Candidate(title, reason, canonicalBookId, matches.size() + feedbackBoost(feedback.get(canonicalBookId))
                        + (favoriteBookIds.contains(canonicalBookId) ? 2 : 0), 0, vectorScore));
            }
            // Hot shelf signals complement semantic preference when a user has little personal history.
            try {
                R<List<Map<String, Object>>> hotResponse = shelfClient.hot(agentProperties.getInternalToken(), 12);
                for (Map<String, Object> hot : hotResponse == null || hotResponse.getData() == null ? List.<Map<String, Object>>of() : hotResponse.getData()) {
                    Long canonicalBookId = parseBookId(hot.get("canonicalBookId"));
                    if (canonicalBookId == null || shelfBookIds.contains(canonicalBookId) || isExcluded(feedback.get(canonicalBookId))) continue;
                    String title = String.valueOf(hot.getOrDefault("title", "热门作品"));
                    candidates.add(new Candidate(title, "根据读者加入书架的情况推荐。", canonicalBookId, 0, 0,
                            Math.min(0.15d, parseHotScore(hot.get("shelfCount")) / 1000d)));
                }
            } catch (Exception ignored) { /* Popularity is an optional ranking signal. */ }
            boolean treatment = experimentService.treatment(userId);
            List<Map<String, String>> results = candidates.stream()
                    .sorted(Comparator.comparingInt(Candidate::preferenceMatches).reversed()
                            .thenComparing(treatment ? Comparator.comparingInt(Candidate::hasProgress).reversed() : Comparator.comparingDouble(Candidate::vectorScore).reversed())
                            .thenComparing(treatment ? Comparator.comparingDouble(Candidate::vectorScore).reversed() : Comparator.comparingInt(Candidate::hasProgress).reversed())
                            .thenComparing(Comparator.comparingInt(Candidate::hasProgress).reversed()))
                    .filter(candidate -> candidate.canonicalBookId() != null)
                    .collect(java.util.stream.Collectors.toMap(Candidate::canonicalBookId, candidate -> candidate, (left, right) -> left, java.util.LinkedHashMap::new)).values().stream()
                    .limit(5).map(candidate -> Map.of("title", candidate.title(), "reason", candidate.reason(), "canonicalBookId", candidate.canonicalBookId().toString()))
                    .toList();
            List<Map<String, String>> finalResults = results.isEmpty() ? List.of(Map.of("title", shelfBooks.isEmpty() ? "先建立你的书架" : "暂时没有合适的书架推荐", "reason",
                    shelfBooks.isEmpty() ? "先添加作品，或等待作品完成索引后再获取个性化推荐。"
                            : "你设置的避开内容暂时筛除了可推荐的书架作品。")) : results;
            experimentService.recordExposure(userId, finalResults.size());
            return finalResults;
        } catch (Exception ignored) {
            return List.of(Map.of("title", "书架暂时不可用", "reason", "你的阅读数据没有改变，请稍后重试。"));
        }
    }

    @Override
    public ReadingPlanVO readingPlan(long userId) {
        try {
            R<List<Map<String, Object>>> response = shelfClient.list(agentProperties.getInternalToken(), userId);
            List<Map<String, Object>> books = response == null || response.getData() == null ? List.of() : response.getData();
            List<ReadingPlanVO.Item> items = books.stream()
                    .filter(book -> parseBookId(book.get("canonicalBookId")) != null)
                    .sorted(Comparator.comparing((Map<String, Object> book) -> book.get("lastReadAt") == null).thenComparing(book -> String.valueOf(book.getOrDefault("lastReadAt", "")), Comparator.reverseOrder()))
                    .limit(3)
                    .map(book -> {
                        int current = Math.max(0, parseInteger(book.get("lastChapterIndex"), 0));
                        Integer total = parsePositiveInteger(book.get("totalChapters"));
                        int target = total != null && total - current <= 2 ? 1 : 2;
                        String title = String.valueOf(book.getOrDefault("bookName", "未命名作品"));
                        String reason = book.get("lastChapterName") == null
                                ? "从第一章开始轻松阅读，再决定是否继续。"
                                : "从已确认的阅读进度继续，不会使用未读剧情信息。";
                        return new ReadingPlanVO.Item(parseBookId(book.get("canonicalBookId")), title, current, total, target, reason);
                    }).toList();
            int target = items.stream().mapToInt(ReadingPlanVO.Item::getSuggestedChaptersToday).sum();
            String summary = items.isEmpty()
                    ? "将可阅读的作品加入书架后，即可依据已确认的阅读进度生成计划。"
                    : "今天的轻量计划：从你最近阅读的作品中安排 " + target + " 章。";
            return new ReadingPlanVO(target, items.size(), summary, items);
        } catch (Exception ignored) {
            return new ReadingPlanVO(0, 0, "书架暂时不可用，未更改任何阅读记录。", List.of());
        }
    }

    private List<String> normalize(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).toList();
    }

    private boolean containsAny(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private Long parseBookId(Object value) {
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; }
    }

    private int parseInteger(Object value, int fallback) {
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
    }

    private Integer parsePositiveInteger(Object value) {
        int parsed = parseInteger(value, 0);
        return parsed > 0 ? parsed : null;
    }

    private int feedbackBoost(String action) {
        if ("COMPLETE".equals(action) || "ADD_TO_SHELF".equals(action)) return 3;
        if ("LIKE".equals(action) || "OPEN".equals(action)) return 2;
        return "CLICK".equals(action) ? 1 : 0;
    }

    private boolean isExcluded(String action) {
        return "DISMISS".equals(action) || "COMPLETE".equals(action);
    }

    private Map<String, Object> canonicalDetail(long canonicalBookId) {
        try {
            R<Map<String, Object>> response = canonicalBookClient.detail(agentProperties.getInternalToken(), canonicalBookId);
            return response == null || response.getData() == null ? Map.of() : response.getData();
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private boolean hasReadableSource(Map<String, Object> detail) {
        Object sourceId = detail.get("sourceId");
        Object sourceBookUrl = detail.get("sourceBookUrl");
        return sourceId != null && sourceBookUrl != null && !String.valueOf(sourceBookUrl).isBlank();
    }

    private double preferenceSimilarity(KnowledgeVectorProfile preference, long canonicalBookId) {
        KnowledgeVectorProfile book = vectorProfileMapper.selectOne(Wrappers.<KnowledgeVectorProfile>lambdaQuery()
                .eq(KnowledgeVectorProfile::getProfileType, "BOOK").eq(KnowledgeVectorProfile::getCanonicalBookId, canonicalBookId)
                .isNull(KnowledgeVectorProfile::getDeletedAt));
        if (book == null) return 0d;
        try {
            List<Double> left = objectMapper.readValue(preference.getEmbeddingJson(), new TypeReference<List<Double>>() { });
            List<Double> right = objectMapper.readValue(book.getEmbeddingJson(), new TypeReference<List<Double>>() { });
            return embeddingService.similarity(left, right);
        } catch (Exception ignored) { return 0d; }
    }
    private double parseHotScore(Object value) { try { return Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) { return 0d; } }

    private record Candidate(String title, String reason, Long canonicalBookId, int preferenceMatches, int hasProgress, double vectorScore) { }
}
