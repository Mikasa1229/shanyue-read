package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 记录阅读时长请求体
 */
@Data
public class ReadingRecordDTO {

    /** 阅读时长（秒），最少 1 秒 */
    @NotNull
    @Min(1)
    private Integer seconds;
}
