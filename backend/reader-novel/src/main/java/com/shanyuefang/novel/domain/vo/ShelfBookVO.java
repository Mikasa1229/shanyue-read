package com.shanyuefang.novel.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShelfBookVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long canonicalBookId;
    private String sourceName;
    private String bookName;
    private String author;
    private String coverUrl;
    private String bookUrl;
    private String lastChapterName;
    private String lastChapterUrl;
    private Integer lastChapterIndex;
    private Integer totalChapters;
    private LocalDateTime lastReadAt;
    private LocalDateTime createdAt;
}
