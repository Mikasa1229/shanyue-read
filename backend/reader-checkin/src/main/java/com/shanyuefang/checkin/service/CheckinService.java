package com.shanyuefang.checkin.service;

import com.shanyuefang.checkin.domain.dto.CheckinDTO;
import com.shanyuefang.checkin.domain.vo.CheckinCalendarVO;
import com.shanyuefang.checkin.domain.vo.StreakVO;

public interface CheckinService {

    /** 今日打卡（幂等） */
    void checkin(Long userId, CheckinDTO dto);

    /** 查询某月打卡日历 */
    CheckinCalendarVO getMonthCalendar(Long userId, int year, int month);

    /** 查询连续打卡天数 & 累计统计 */
    StreakVO getStreak(Long userId);
}
