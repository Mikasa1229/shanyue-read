package com.shanyuefang.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CanonicalBookVO {
    private Long canonicalBookId;
    private boolean newlyCreated;
}
