package com.shanyuefang.novel.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyuefang.common.result.R;
import com.shanyuefang.novel.domain.dto.CreateNovelDTO;
import com.shanyuefang.novel.domain.dto.NovelPageDTO;
import com.shanyuefang.novel.domain.dto.UpdateNovelDTO;
import com.shanyuefang.novel.domain.vo.NovelVO;
import com.shanyuefang.novel.service.NovelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小说接口")
@Validated
@RestController
@RequestMapping("/api/novels")
@RequiredArgsConstructor
public class NovelController {

    private final NovelService novelService;

    @Operation(summary = "发布小说")
    @PostMapping
    public R<NovelVO> create(@RequestHeader("X-User-Id") Long userId,
                             @Valid @RequestBody CreateNovelDTO dto) {
        return R.ok(novelService.create(userId, dto));
    }

    @Operation(summary = "编辑小说")
    @PutMapping("/{id}")
    public R<NovelVO> update(@RequestHeader("X-User-Id") Long userId,
                             @PathVariable Long id,
                             @Valid @RequestBody UpdateNovelDTO dto) {
        return R.ok(novelService.update(userId, id, dto));
    }

    @Operation(summary = "删除小说")
    @DeleteMapping("/{id}")
    public R<Void> delete(@RequestHeader("X-User-Id") Long userId,
                          @PathVariable Long id) {
        novelService.delete(userId, id);
        return R.ok();
    }

    @Operation(summary = "分页查询小说列表（公开）")
    @GetMapping
    public R<Page<NovelVO>> getPage(@Valid NovelPageDTO dto) {
        return R.ok(novelService.getPage(dto));
    }

    @Operation(summary = "查询小说详情（公开，累加浏览量）")
    @GetMapping("/{id}")
    public R<NovelVO> getDetail(@PathVariable Long id) {
        return R.ok(novelService.getDetail(id));
    }

    @Operation(summary = "我发布的小说")
    @GetMapping("/my")
    public R<Page<NovelVO>> getMyNovels(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return R.ok(novelService.getMyNovels(userId, page, size));
    }
}
