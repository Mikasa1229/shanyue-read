package com.shanyuefang.novel.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyuefang.novel.domain.dto.AddFavoriteDTO;
import com.shanyuefang.novel.domain.vo.FavoriteBookVO;

public interface FavoriteService {

    /** 添加收藏（幂等，已存在则更新元数据） */
    void addFavorite(long userId, AddFavoriteDTO dto);

    /** 取消收藏，不存在时抛异常 */
    void removeFavorite(long userId, Long canonicalBookId, String bookUrl);

    /** Legacy URL-only removal for callers that predate canonical work identity. */
    default void removeFavorite(long userId, String bookUrl) {
        removeFavorite(userId, null, bookUrl);
    }

    /** 分页查询我的收藏列表 */
    Page<FavoriteBookVO> listMyFavorites(long userId, int page, int size);

    /** 检查某本书是否已收藏 */
    boolean isFavorited(long userId, String bookUrl);
    boolean isFavorited(long userId, Long canonicalBookId, String bookUrl);
}
