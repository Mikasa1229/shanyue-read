package com.shanyuefang.novel.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyuefang.common.result.R;
import com.shanyuefang.novel.domain.vo.ShelfBookVO;
import com.shanyuefang.novel.service.BookshelfService;
import com.shanyuefang.novel.service.FavoriteService;
import com.shanyuefang.novel.service.NovelInternalAccess;
import com.shanyuefang.novel.mapper.BookSourceMappingMapper;
import com.shanyuefang.novel.domain.entity.BookSourceMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Private, bounded shelf projection used by the recommendation tool. */
@RestController
@RequestMapping("/internal/bookshelves")
@RequiredArgsConstructor
public class InternalBookshelfController {
    private final BookshelfService bookshelfService;
    private final FavoriteService favoriteService;
    private final BookSourceMappingMapper mappingMapper;
    private final NovelInternalAccess internalAccess;

    @GetMapping
    public R<List<ShelfBookVO>> list(@RequestHeader("X-Agent-Internal-Token") String token, @RequestParam long userId) {
        internalAccess.require(token);
        Page<ShelfBookVO> page = bookshelfService.listMyShelf(userId, 1, 30);
        return R.ok(page.getRecords());
    }
    @GetMapping("/hot")
    public R<List<Map<String, Object>>> hot(@RequestHeader("X-Agent-Internal-Token") String token, @RequestParam(defaultValue = "12") int limit) {
        internalAccess.require(token);
        return R.ok(bookshelfService.getHotBooks(Math.max(1, Math.min(limit, 20))).stream().map(book -> {
            BookSourceMapping mapping = book.getCanonicalBookId() == null ? mappingMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<BookSourceMapping>lambdaQuery()
                    .eq(BookSourceMapping::getSourceId, book.getSourceId()).eq(BookSourceMapping::getSourceBookUrl, book.getBookUrl())) : null;
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("title", book.getBookName()); result.put("author", book.getAuthor() == null ? "" : book.getAuthor());
            result.put("sourceId", book.getSourceId() == null ? 0L : book.getSourceId()); result.put("bookUrl", book.getBookUrl()); result.put("shelfCount", book.getShelfCount());
            if (book.getCanonicalBookId() != null) result.put("canonicalBookId", book.getCanonicalBookId());
            else if (mapping != null) result.put("canonicalBookId", mapping.getCanonicalBookId());
            return result;
        }).toList());
    }
    @GetMapping("/favorites")
    public R<List<Long>> favorites(@RequestHeader("X-Agent-Internal-Token") String token, @RequestParam long userId) {
        internalAccess.require(token);
        return R.ok(favoriteService.listMyFavorites(userId, 1, 100).getRecords().stream().map(book -> {
            return book.getCanonicalBookId();
        }).filter(java.util.Objects::nonNull).distinct().toList());
    }
    @GetMapping("/reading-boundary")
    public R<Map<String, Integer>> readingBoundary(@RequestHeader("X-Agent-Internal-Token") String token,
                                                    @RequestParam long userId, @RequestParam long canonicalBookId) {
        internalAccess.require(token);
        Page<ShelfBookVO> shelf = bookshelfService.listMyShelf(userId, 1, 100);
        int boundary = shelf.getRecords().stream()
                .filter(book -> canonicalBookId == (book.getCanonicalBookId() == null ? -1L : book.getCanonicalBookId()))
                .map(ShelfBookVO::getLastChapterIndex).filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0);
        return R.ok(Map.of("currentChapter", Math.max(0, boundary)));
    }
}
