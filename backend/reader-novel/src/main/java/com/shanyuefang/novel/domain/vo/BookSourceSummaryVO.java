package com.shanyuefang.novel.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/** A readable source mirror belonging to one canonical work. */
@Data
public class BookSourceSummaryVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceId;
    private String sourceName;
    private String bookUrl;
    private String lastChapter;
    private String availability;
}
