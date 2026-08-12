package com.shanyuefang.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CanonicalBookDetailVO {
    private Long canonicalBookId;
    private String title;
    private String author;
    private String coverUrl;
    private String summary;
    private Long sourceId;
    private String sourceBookUrl;
}
