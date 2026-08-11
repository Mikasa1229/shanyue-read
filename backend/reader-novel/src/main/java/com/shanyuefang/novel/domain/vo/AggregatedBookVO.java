package com.shanyuefang.novel.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/** Search projection: one canonical work with all currently discovered mirrors. */
@Data
public class AggregatedBookVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long canonicalBookId;
    private String name;
    private String author;
    private String coverUrl;
    private String intro;
    private String kind;
    private String lastChapter;
    private int sourceCount;
    private BookSourceSummaryVO preferredSource;
    private List<BookSourceSummaryVO> sources;
}
