package com.shanyuefang.interaction.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyuefang.common.result.R;
import com.shanyuefang.interaction.domain.dto.InteractionDTO;
import com.shanyuefang.interaction.domain.dto.InteractionStatusDTO;
import com.shanyuefang.interaction.domain.dto.ToggleInteractionDTO;
import com.shanyuefang.interaction.domain.vo.InteractionResultVO;
import com.shanyuefang.interaction.feign.vo.NovelSimpleVO;
import com.shanyuefang.interaction.service.InteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "互动接口", description = "点赞 / 收藏")
@Validated
@RestController
@RequestMapping("/api/interactions")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    @Operation(summary = "点赞 / 取消点赞（幂等切换）")
    @PostMapping("/like")
    public R<InteractionResultVO> toggleLike(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody InteractionDTO dto) {
        return R.ok(interactionService.toggleLike(userId, dto.getTargetId(), dto.getTargetType()));
    }

    @Operation(summary = "收藏 / 取消收藏（仅小说，幂等切换）")
    @PostMapping("/favorite")
    public R<InteractionResultVO> toggleFavorite(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody InteractionDTO dto) {
        return R.ok(interactionService.toggleFavorite(userId, dto.getTargetId()));
    }

    @Operation(summary = "统一切换互动（点赞或收藏，由 action 字段决定）")
    @PostMapping("/toggle")
    public R<InteractionResultVO> toggle(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ToggleInteractionDTO dto) {
        return R.ok(interactionService.toggle(userId, dto));
    }

    @Operation(summary = "批量查询当前用户的互动状态（含计数）")
    @PostMapping("/status")
    public R<Map<Long, InteractionResultVO>> batchQueryStatus(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody InteractionStatusDTO dto) {
        return R.ok(interactionService.batchQueryStatusWithCount(userId, dto));
    }

    @Operation(summary = "我的收藏列表（分页）")
    @GetMapping("/my-favorites")
    public R<Page<NovelSimpleVO>> getMyFavorites(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return R.ok(interactionService.getMyFavorites(userId, page, size));
    }
}
