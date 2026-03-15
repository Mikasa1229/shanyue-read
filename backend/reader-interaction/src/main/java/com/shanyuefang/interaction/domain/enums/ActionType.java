package com.shanyuefang.interaction.domain.enums;

import lombok.Getter;

/**
 * 互动行为类型
 */
@Getter
public enum ActionType {
    LIKE(1, "点赞"),
    FAVORITE(2, "收藏");

    private final int code;
    private final String desc;

    ActionType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
