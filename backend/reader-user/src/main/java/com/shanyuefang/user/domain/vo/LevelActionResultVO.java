package com.shanyuefang.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LevelActionResultVO {

    private int gainedExp;
    private long expTotal;
    private int level;
}
