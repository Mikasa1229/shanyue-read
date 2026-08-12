package com.shanyuefang.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReadingSessionVO {
    private String sessionToken;
    private long qualifiedSeconds;
    private boolean rewardGranted;
}
