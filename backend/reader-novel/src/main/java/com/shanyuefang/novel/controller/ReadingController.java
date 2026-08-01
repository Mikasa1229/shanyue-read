package com.shanyuefang.novel.controller;

import com.shanyuefang.common.result.R;
import com.shanyuefang.novel.domain.dto.ReadingHeartbeatDTO;
import com.shanyuefang.novel.domain.dto.StartReadingSessionDTO;
import com.shanyuefang.novel.domain.vo.RankingVO;
import com.shanyuefang.novel.domain.vo.ReadingSessionVO;
import com.shanyuefang.novel.service.ReadingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "阅读记录与排行", description = "记录阅读时长、查询排行榜")
@RestController
@RequestMapping("/api/reading")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingService readingService;

    @Operation(summary = "记录本次阅读时长（需登录）")
    @PostMapping("/sessions")
    public R<ReadingSessionVO> startSession(@RequestHeader("X-User-Id") Long userId,
                                             @Valid @RequestBody StartReadingSessionDTO dto) {
        return R.ok(readingService.startSession(userId, dto));
    }

    @PostMapping("/sessions/heartbeat")
    public R<ReadingSessionVO> heartbeat(@RequestHeader("X-User-Id") Long userId,
                                          @Valid @RequestBody ReadingHeartbeatDTO dto) {
        return R.ok(readingService.heartbeat(userId, dto));
    }

    @Operation(summary = "获取阅读时长排行榜（公开）")
    @GetMapping("/ranking")
    public R<List<RankingVO>> ranking(
            @RequestParam(name = "top", defaultValue = "50") int top) {
        return R.ok(readingService.getRanking(top));
    }
}
