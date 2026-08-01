package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.agent.domain.dto.RecommendationFeedbackDTO;
import com.shanyuefang.agent.domain.entity.RecommendationFeedback;
import com.shanyuefang.agent.mapper.RecommendationFeedbackMapper;
import com.shanyuefang.agent.service.RecommendationFeedbackService;
import com.shanyuefang.agent.service.RecommendationExperimentService;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationFeedbackServiceImpl implements RecommendationFeedbackService {
    private final RecommendationFeedbackMapper feedbackMapper;
    private final RecommendationExperimentService experimentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(long userId, RecommendationFeedbackDTO dto) {
        RecommendationFeedback feedback = feedbackMapper.selectOne(Wrappers.<RecommendationFeedback>lambdaQuery()
                .eq(RecommendationFeedback::getUserId, userId)
                .eq(RecommendationFeedback::getCanonicalBookId, dto.getCanonicalBookId()));
        if (feedback == null) {
            feedback = new RecommendationFeedback();
            feedback.setId(SnowflakeIdUtil.next());
            feedback.setUserId(userId);
            feedback.setCanonicalBookId(dto.getCanonicalBookId());
            feedback.setCreatedAt(LocalDateTime.now());
        }
        feedback.setAction(dto.getAction());
        // Resolve the group on the server so clients cannot forge experiment attribution.
        feedback.setExperimentVariant(experimentService.variant(userId));
        feedback.setUpdatedAt(LocalDateTime.now());
        if (feedbackMapper.selectById(feedback.getId()) == null) feedbackMapper.insert(feedback); else feedbackMapper.updateById(feedback);
    }

    @Override
    public Map<Long, String> feedbackByBook(long userId) {
        return feedbackMapper.selectList(Wrappers.<RecommendationFeedback>lambdaQuery()
                        .eq(RecommendationFeedback::getUserId, userId))
                .stream().collect(Collectors.toMap(RecommendationFeedback::getCanonicalBookId,
                        RecommendationFeedback::getAction, (left, right) -> right));
    }
}
