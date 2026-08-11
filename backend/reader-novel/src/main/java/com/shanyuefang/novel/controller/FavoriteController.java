package com.shanyuefang.novel.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyuefang.common.result.R;
import com.shanyuefang.novel.domain.dto.AddFavoriteDTO;
import com.shanyuefang.novel.domain.vo.FavoriteBookVO;
import com.shanyuefang.novel.service.FavoriteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "收藏")
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** 添加收藏（幂等） */
    @PostMapping
    public R<Void> add(@RequestHeader("X-User-Id") Long userId,
                       @Valid @RequestBody AddFavoriteDTO dto) {
        favoriteService.addFavorite(userId, dto);
        return R.ok();
    }

    /** 取消收藏 */
    @DeleteMapping
    public R<Void> remove(@RequestHeader("X-User-Id") Long userId,
                          @RequestParam(required = false) Long canonicalBookId,
                          @RequestParam(required = false) String bookUrl) {
        favoriteService.removeFavorite(userId, canonicalBookId, bookUrl);
        return R.ok();
    }

    /** 我的收藏（分页） */
    @GetMapping
    public R<Page<FavoriteBookVO>> list(@RequestHeader("X-User-Id") Long userId,
                                        @RequestParam(defaultValue = "1")  int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return R.ok(favoriteService.listMyFavorites(userId, page, size));
    }

    /** 检查某本书是否已收藏 */
    @GetMapping("/check")
    public R<Map<String, Boolean>> check(@RequestHeader("X-User-Id") Long userId,
                                         @RequestParam(required = false) Long canonicalBookId,
                                         @RequestParam(required = false) String bookUrl) {
        boolean favorited = favoriteService.isFavorited(userId, canonicalBookId, bookUrl);
        return R.ok(Map.of("favorited", favorited));
    }
}
