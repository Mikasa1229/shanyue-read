package com.shanyuefang.novel.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyuefang.novel.domain.dto.CreateNovelDTO;
import com.shanyuefang.novel.domain.dto.NovelPageDTO;
import com.shanyuefang.novel.domain.dto.UpdateNovelDTO;
import com.shanyuefang.novel.domain.entity.Novel;
import com.shanyuefang.novel.domain.vo.NovelSimpleVO;
import com.shanyuefang.novel.domain.vo.NovelVO;

import java.util.List;
import java.util.Map;

public interface NovelService extends IService<Novel> {

    /** 发布小说 */
    NovelVO create(Long userId, CreateNovelDTO dto);

    /** 编辑小说（仅作者本人） */
    NovelVO update(Long userId, Long id, UpdateNovelDTO dto);

    /** 软删除（仅作者本人） */
    void delete(Long userId, Long id);

    /** 分页列表（公开） */
    Page<NovelVO> getPage(NovelPageDTO dto);

    /** 查询详情（同时累加浏览量） */
    NovelVO getDetail(Long id);

    /** 我发布的小说 */
    Page<NovelVO> getMyNovels(Long userId, int page, int size);

    /** 批量查询简要信息（内部 Feign 调用） */
    Map<Long, NovelSimpleVO> batchGet(List<Long> ids);

    /** 更新点赞数（delta = +1 / -1） */
    void updateLikeCount(Long novelId, int delta);

    /** 更新收藏数（delta = +1 / -1） */
    void updateFavoriteCount(Long novelId, int delta);
}
