package com.shanyuefang.checkin.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 月度打卡日历 VO
 */
@Data
public class CheckinCalendarVO {

    private int year;
    private int month;
    /** 当月已打卡的日期列表（1-31）*/
    private List<Integer> checkedDays;
    /** 当月打卡总天数 */
    private int totalDays;
}
