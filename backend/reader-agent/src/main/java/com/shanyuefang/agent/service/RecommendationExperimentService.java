package com.shanyuefang.agent.service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.agent.domain.entity.RecommendationExperiment;
import com.shanyuefang.agent.mapper.RecommendationExperimentMapper;
import com.shanyuefang.agent.mapper.RecommendationExposureMapper;
import com.shanyuefang.agent.mapper.RecommendationFeedbackMapper;
import com.shanyuefang.agent.domain.dto.RecommendationExperimentDTO;
import com.shanyuefang.agent.domain.entity.RecommendationExposure;
import com.shanyuefang.agent.domain.entity.RecommendationFeedback;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
@Service @RequiredArgsConstructor public class RecommendationExperimentService {
 private static final String EXPERIMENT_KEY = "recommendation-ranking-v1";
 private final RecommendationExperimentMapper mapper;
 private final RecommendationExposureMapper exposureMapper;
 private final RecommendationFeedbackMapper feedbackMapper;
 public boolean treatment(long userId) { RecommendationExperiment e=current(); return e!=null && Boolean.TRUE.equals(e.getEnabled()) && Math.floorMod(Long.hashCode(userId),100)<Math.max(0,Math.min(100,e.getTreatmentPercent())); }
 public String variant(long userId) { RecommendationExperiment e=current(); return e != null && Boolean.TRUE.equals(e.getEnabled()) ? (treatment(userId) ? "TREATMENT" : "CONTROL") : "BASELINE"; }
 public void recordExposure(long userId, int recommendationCount) { String variant = variant(userId); if ("BASELINE".equals(variant)) return; RecommendationExposure exposure = new RecommendationExposure(); exposure.setId(SnowflakeIdUtil.next()); exposure.setUserId(userId); exposure.setExperimentKey(EXPERIMENT_KEY); exposure.setExperimentVariant(variant); exposure.setRecommendationCount(recommendationCount); exposure.setCreatedAt(LocalDateTime.now()); exposureMapper.insert(exposure); }
 public java.util.Map<String, Object> metrics(int days) { LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 90))); java.util.Map<String, Object> values = new java.util.LinkedHashMap<>(); for (String variant : java.util.List.of("CONTROL", "TREATMENT")) { long exposures = exposureMapper.selectCount(Wrappers.<RecommendationExposure>lambdaQuery().eq(RecommendationExposure::getExperimentKey, EXPERIMENT_KEY).eq(RecommendationExposure::getExperimentVariant, variant).ge(RecommendationExposure::getCreatedAt, since)); java.util.List<RecommendationFeedback> feedback = feedbackMapper.selectList(Wrappers.<RecommendationFeedback>lambdaQuery().eq(RecommendationFeedback::getExperimentVariant, variant).ge(RecommendationFeedback::getUpdatedAt, since)); long positive = feedback.stream().filter(item -> java.util.Set.of("OPEN", "CLICK", "LIKE", "ADD_TO_SHELF", "COMPLETE").contains(item.getAction())).count(); long adds = feedback.stream().filter(item -> "ADD_TO_SHELF".equals(item.getAction())).count(); values.put(variant.toLowerCase(java.util.Locale.ROOT), java.util.Map.of("exposures", exposures, "positiveFeedback", positive, "addsToShelf", adds, "positiveRate", exposures == 0 ? 0D : (double) positive / exposures)); } return values; }
 public RecommendationExperiment current() { return mapper.selectOne(Wrappers.<RecommendationExperiment>lambdaQuery().eq(RecommendationExperiment::getExperimentKey, EXPERIMENT_KEY).last("LIMIT 1")); }
 public RecommendationExperiment save(RecommendationExperimentDTO dto) { RecommendationExperiment e=current(); if(e==null){e=new RecommendationExperiment();e.setId(SnowflakeIdUtil.next());e.setExperimentKey(EXPERIMENT_KEY);e.setCreatedAt(LocalDateTime.now());} e.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));e.setTreatmentPercent(dto.getTreatmentPercent());e.setUpdatedAt(LocalDateTime.now());if(mapper.selectById(e.getId())==null)mapper.insert(e);else mapper.updateById(e);return e; }
}
