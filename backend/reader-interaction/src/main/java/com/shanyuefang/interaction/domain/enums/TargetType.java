package com.shanyuefang.interaction.domain.enums;

import lombok.Getter;

/**
 * 互动目标类型
 */
@Getter
public enum TargetType {
    NOVEL(1, "小说"),
    COMMENT(2, "点评");

    private final int code;
    private final String desc;

    TargetType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
