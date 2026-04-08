package com.shanyuefang.novel.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoriteBookVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceId;

    private String sourceName;
    private String bookName;
    private String author;
    private String coverUrl;
    private String bookUrl;
    private LocalDateTime createdAt;
}
