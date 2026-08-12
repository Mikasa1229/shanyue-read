package com.shanyuefang.agent.service;

import java.util.List;
import java.util.Map;
import com.shanyuefang.agent.domain.vo.ReadingPlanVO;

public interface RecommendationService {
    List<Map<String, String>> dynamicShelf(long userId);
    ReadingPlanVO readingPlan(long userId);
}
