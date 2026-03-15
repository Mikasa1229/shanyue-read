package com.shanyuefang.checkin.controller;

import com.shanyuefang.checkin.domain.dto.CheckinDTO;
import com.shanyuefang.checkin.domain.vo.CheckinCalendarVO;
import com.shanyuefang.checkin.domain.vo.StreakVO;
import com.shanyuefang.checkin.service.CheckinService;
import com.shanyuefang.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "打卡接口")
@Validated
@RestController
@RequestMapping("/api/checkins")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @Operation(summary = "今日打卡（幂等）")
    @PostMapping
    public R<Void> checkin(@RequestHeader("X-User-Id") Long userId,
                           @Valid @RequestBody CheckinDTO dto) {
        checkinService.checkin(userId, dto);
        return R.ok();
    }

    @Operation(summary = "查询月度打卡日历")
    @GetMapping
    public R<CheckinCalendarVO> getMonthCalendar(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") @Min(0) @Max(12) int month) {
        LocalDate now = LocalDate.now();
        int y = year == 0 ? now.getYear() : year;
        int m = month == 0 ? now.getMonthValue() : month;
        return R.ok(checkinService.getMonthCalendar(userId, y, m));
    }

    @Operation(summary = "查询连续打卡 & 累计统计")
    @GetMapping("/streak")
    public R<StreakVO> getStreak(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(checkinService.getStreak(userId));
    }
}
