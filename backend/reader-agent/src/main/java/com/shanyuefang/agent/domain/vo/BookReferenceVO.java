package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A platform-verified work reference that the client can open directly. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookReferenceVO {
    private Long canonicalBookId;
    private String title;
    private String author;
    private String coverUrl;
    private Long sourceId;
    private String sourceBookUrl;
    private String summary;
}
