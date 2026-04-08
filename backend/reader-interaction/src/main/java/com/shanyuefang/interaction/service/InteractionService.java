package com.shanyuefang.interaction.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyuefang.interaction.domain.dto.InteractionStatusDTO;
import com.shanyuefang.interaction.domain.dto.ToggleInteractionDTO;
import com.shanyuefang.interaction.domain.vo.InteractionResultVO;
import com.shanyuefang.interaction.feign.vo.NovelSimpleVO;

import java.util.Map;

public interface InteractionService {

    /** 点赞 / 取消点赞（幂等）*/
    InteractionResultVO toggleLike(Long userId, Long targetId, Integer targetType);

    /** 收藏 / 取消收藏（幂等，仅支持小说）*/
    InteractionResultVO toggleFavorite(Long userId, Long targetId);

    /** 统一切换（根据 action 分发到点赞/收藏）*/
    InteractionResultVO toggle(Long userId, ToggleInteractionDTO dto);

    /**
     * 批量查询当前用户对一组目标的互动状态（含计数）
     *
     * @return Map<targetId, InteractionResultVO{active, count}>
     */
    Map<Long, InteractionResultVO> batchQueryStatusWithCount(Long userId, InteractionStatusDTO dto);

    /**
     * 查询当前用户收藏的小说列表（分页）
     *
     * @return 分页小说简要信息
     */
    Page<NovelSimpleVO> getMyFavorites(Long userId, int page, int size);
}
