package com.shanyuefang.novel.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 打卡事件（与 reader-checkin 的 CheckinEvent 结构一致）
 */
@Data
@NoArgsConstructor
public class CheckinEvent {

    private Long userId;

    /** 打卡关联的小说 ID（可为 null） */
    private Long novelId;

    private LocalDate checkinDate;

    private String note;
}
