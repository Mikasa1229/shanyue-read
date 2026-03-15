package com.shanyuefang.checkin.domain.vo;

import lombok.Data;

@Data
public class StreakVO {
    /** 当前连续打卡天数 */
    private int currentStreak;
    /** 历史最长连续打卡天数 */
    private int longestStreak;
    /** 累计打卡总天数 */
    private int totalDays;
    /** 今日是否已打卡 */
    private boolean checkedToday;
}
