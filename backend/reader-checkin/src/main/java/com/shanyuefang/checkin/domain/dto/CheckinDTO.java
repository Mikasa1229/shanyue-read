package com.shanyuefang.checkin.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CheckinDTO {

    /** 关联小说 ID，可为空（个人打卡页不绑定具体小说） */
    private Long novelId;

    @Size(max = 500, message = "打卡留言最多 500 字")
    private String note;
}
