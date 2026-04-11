package com.shanyuefang.novel.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyuefang.common.result.R;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.novel.domain.vo.BookChapterVO;
import com.shanyuefang.novel.domain.vo.BookSourceVO;
import com.shanyuefang.novel.domain.vo.SearchBookVO;
import com.shanyuefang.novel.service.BookSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "书源管理", description = "legado 兼容书源的导入 / 搜索 / 章节 / 内容")
@RestController
@RequestMapping("/api/book-sources")
@RequiredArgsConstructor
public class BookSourceController {

    private final BookSourceService bookSourceService;

    // ─── 导入 ─────────────────────────────────────────────────

    @Operation(summary = "从远端 URL 拉取并导入书源（支持 JSON 数组）")
    @PostMapping("/import/url")
    public R<Map<String, Integer>> importFromUrl(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return R.fail(ResultCode.PARAM_ERROR);
        }
        int count = bookSourceService.importFromUrl(url);
        return R.ok(Map.of("imported", count));
    }

    @Operation(summary = "直接粘贴 JSON 文本导入书源")
    @PostMapping("/import/json")
    public R<Map<String, Integer>> importFromJson(@RequestBody Map<String, String> body) {
        String json = body.get("json");
        if (json == null || json.isBlank()) {
            return R.fail(ResultCode.PARAM_ERROR);
        }
        int count = bookSourceService.importFromJson(json);
        return R.ok(Map.of("imported", count));
    }

    // ─── 管理 ─────────────────────────────────────────────────

    @Operation(summary = "分页查询书源列表")
    @GetMapping
    public R<Page<BookSourceVO>> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return R.ok(bookSourceService.listSources(page, size));
    }

    @Operation(summary = "启用 / 禁用书源")
    @PutMapping("/{id}/status")
    public R<Void> toggle(@PathVariable("id") Long id) {
        bookSourceService.toggleEnabled(id);
        return R.ok();
    }

    @Operation(summary = "删除书源")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        bookSourceService.deleteSource(id);
        return R.ok();
    }

    // ─── 搜索 ─────────────────────────────────────────────────

    @Operation(summary = "聚合搜索：并发调用所有启用书源，合并结果")
    @GetMapping("/search")
    public R<List<SearchBookVO>> aggregateSearch(
            @RequestParam(name = "keyword") String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page) {
        return R.ok(bookSourceService.aggregateSearch(keyword, page));
    }

    @Operation(summary = "使用指定书源搜索书籍")
    @GetMapping("/{id}/search")
    public R<List<SearchBookVO>> search(
            @PathVariable("id") Long id,
            @RequestParam(name = "keyword")
            @Parameter(description = "搜索关键词（书名或作者）") String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page) {
        return R.ok(bookSourceService.search(id, keyword, page));
    }

    @Operation(summary = "获取书籍目录（章节列表）",
               description = "bookUrl 为搜索结果中返回的原始书源网站 URL")
    @GetMapping("/{id}/chapters")
    public R<List<BookChapterVO>> chapters(
            @PathVariable("id") Long id,
            @RequestParam(name = "bookUrl")
            @Parameter(description = "书籍详情页 URL") String bookUrl) {
        return R.ok(bookSourceService.getChapters(id, bookUrl));
    }

    @Operation(summary = "分页获取书籍目录（默认每次 50 章）")
    @GetMapping("/{id}/chapters/page")
    public R<Map<String, Object>> chaptersPage(
            @PathVariable("id") Long id,
            @RequestParam(name = "bookUrl") String bookUrl,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return R.ok(bookSourceService.getChaptersPage(id, bookUrl, offset, limit));
    }

    @Operation(summary = "获取书源书籍详情",
               description = "用于详情页补齐封面、简介、作者等信息")
    @GetMapping("/{id}/detail")
    public R<SearchBookVO> detail(
            @PathVariable("id") Long id,
            @RequestParam(name = "bookUrl") String bookUrl) {
        return R.ok(bookSourceService.getBookDetail(id, bookUrl));
    }

    @Operation(summary = "获取章节正文内容",
               description = "chapterUrl 为目录接口返回的章节 URL")
    @GetMapping("/{id}/content")
    public R<Map<String, String>> content(
            @PathVariable("id") Long id,
            @RequestParam(name = "chapterUrl")
            @Parameter(description = "章节 URL") String chapterUrl) {
        String text = bookSourceService.getContent(id, chapterUrl);
        return R.ok(Map.of("content", text));
    }

    @Operation(summary = "测试书源是否可访问")
    @GetMapping("/{id}/test")
    public R<Map<String, Object>> test(@PathVariable("id") Long id) {
        return R.ok(bookSourceService.testSource(id));
    }

    @Operation(summary = "调试：查看章节提取中间结果")
    @GetMapping("/{id}/debug-chapters")
    public R<Map<String, Object>> debugChapters(
            @PathVariable("id") Long id,
            @RequestParam("bookUrl") String bookUrl) {
        return R.ok(bookSourceService.debugChapters(id, bookUrl));
    }
}
