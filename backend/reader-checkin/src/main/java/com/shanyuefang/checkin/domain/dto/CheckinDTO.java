package com.shanyuefang.checkin.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CheckinDTO {

    @NotNull(message = "小说 ID 不能为空")
    private Long novelId;

    @Size(max = 500, message = "打卡留言最多 500 字")
    private String note;
}
