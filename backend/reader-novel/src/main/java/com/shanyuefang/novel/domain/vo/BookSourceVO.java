package com.shanyuefang.novel.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookSourceVO {
    /** 序列化为字符串，防止 JavaScript 大整数精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String sourceName;
    private String sourceUrl;
    private Integer sourceType;
    private String sourceGroup;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
