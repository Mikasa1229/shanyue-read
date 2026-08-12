package com.shanyuefang.novel.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyuefang.common.result.R;
import com.shanyuefang.novel.domain.dto.AddToShelfDTO;
import com.shanyuefang.novel.domain.dto.UpdateProgressDTO;
import com.shanyuefang.novel.domain.vo.HotBookVO;
import com.shanyuefang.novel.domain.vo.ShelfBookVO;
import com.shanyuefang.novel.service.BookshelfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookshelf")
@RequiredArgsConstructor
public class BookshelfController {

    private final BookshelfService bookshelfService;

    /** 加入书架（幂等） */
    @PostMapping
    public R<Void> add(@RequestHeader("X-User-Id") Long userId,
                       @Valid @RequestBody AddToShelfDTO dto) {
        bookshelfService.addBook(userId, dto);
        return R.ok();
    }

    /** 移出书架 */
    @DeleteMapping
    public R<Void> remove(@RequestHeader("X-User-Id") Long userId,
                          @RequestParam(required = false) Long canonicalBookId,
                          @RequestParam(required = false) String bookUrl) {
        bookshelfService.removeBook(userId, canonicalBookId, bookUrl);
        return R.ok();
    }

    /** 我的书架（分页） */
    @GetMapping
    public R<Page<ShelfBookVO>> list(@RequestHeader("X-User-Id") Long userId,
                                     @RequestParam(defaultValue = "1")  int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(bookshelfService.listMyShelf(userId, page, size));
    }

    /** 检查某本书是否已在书架 */
    @GetMapping("/check")
    public R<Map<String, Boolean>> check(@RequestHeader("X-User-Id") Long userId,
                                         @RequestParam(required = false) Long canonicalBookId,
                                         @RequestParam(required = false) String bookUrl) {
        boolean onShelf = bookshelfService.isOnShelf(userId, canonicalBookId, bookUrl);
        return R.ok(Map.of("onShelf", onShelf));
    }

    /** 更新阅读进度 */
    @PutMapping("/progress")
    public R<Void> progress(@RequestHeader("X-User-Id") Long userId,
                            @Valid @RequestBody UpdateProgressDTO dto) {
        bookshelfService.updateProgress(userId, dto);
        return R.ok();
    }

    /** 热门书籍排行榜（按加入书架用户数排序，无需登录） */
    @GetMapping("/hot")
    public R<List<HotBookVO>> hot(@RequestParam(defaultValue = "20") int top) {
        return R.ok(bookshelfService.getHotBooks(top));
    }
}
