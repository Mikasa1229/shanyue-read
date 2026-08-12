package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Resolves the server-owned reading boundary; client chapter values can only narrow it. */
@Service
@RequiredArgsConstructor
public class SpoilerBoundaryService {
    private final NovelShelfFeignClient shelfClient;
    private final AgentProperties properties;

    public int clamp(long userId, long canonicalBookId, int requestedChapter) {
        return clamp(userId, canonicalBookId, requestedChapter, false);
    }

    /** A reader may explicitly opt into spoilers for a deliberate retrospective analysis. */
    public int clamp(long userId, long canonicalBookId, int requestedChapter, boolean spoilersConfirmed) {
        if (spoilersConfirmed) return Math.max(0, requestedChapter);
        try {
            R<Map<String, Integer>> response = shelfClient.readingBoundary(properties.getInternalToken(), userId, canonicalBookId);
            int serverBoundary = response == null || response.getData() == null ? 0 : response.getData().getOrDefault("currentChapter", 0);
            return Math.min(Math.max(0, requestedChapter), Math.max(0, serverBoundary));
        } catch (Exception ignored) {
            // Fail closed: an unavailable progress service must not broaden the spoiler boundary.
            return 0;
        }
    }
}
