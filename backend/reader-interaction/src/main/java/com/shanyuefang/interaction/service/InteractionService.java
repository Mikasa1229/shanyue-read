package com.shanyuefang.interaction.service;

import com.shanyuefang.interaction.domain.dto.InteractionStatusDTO;
import com.shanyuefang.interaction.domain.vo.InteractionResultVO;

import java.util.Map;

public interface InteractionService {

    /** 点赞 / 取消点赞（幂等）*/
    InteractionResultVO toggleLike(Long userId, Long targetId, Integer targetType);

    /** 收藏 / 取消收藏（幂等，仅支持小说）*/
    InteractionResultVO toggleFavorite(Long userId, Long targetId);

    /**
     * 批量查询当前用户对一组目标的互动状态
     *
     * @return Map<targetId, Boolean> true=已操作
     */
    Map<Long, Boolean> batchQueryStatus(Long userId, InteractionStatusDTO dto);
}
