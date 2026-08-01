package com.shanyuefang.agent.service;

import com.shanyuefang.agent.domain.dto.RecommendationFeedbackDTO;

import java.util.Map;

public interface RecommendationFeedbackService {
    void save(long userId, RecommendationFeedbackDTO dto);
    Map<Long, String> feedbackByBook(long userId);
}
