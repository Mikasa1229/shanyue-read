package com.shanyuefang.checkin.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 打卡事件（发布到 reader.topic，routing key = checkin.created）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckinEvent {

    /** 打卡用户 ID */
    private Long userId;

    /** 打卡关联的小说 ID（可为 null） */
    private Long novelId;

    /** 打卡日期 */
    private LocalDate checkinDate;

    /** 打卡备注 */
    private String note;
}
