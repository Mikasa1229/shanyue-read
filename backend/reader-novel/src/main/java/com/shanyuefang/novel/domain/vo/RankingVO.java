package com.shanyuefang.novel.domain.vo;

import lombok.Data;

/**
 * 阅读时间排行榜条目
 */
@Data
public class RankingVO {

    /** 名次（从 1 开始） */
    private int rank;

    /** 用户 ID */
    private Long userId;

    /** 用户昵称 */
    private String nickname;

    /** 用户头像 URL */
    private String avatar;

    /** 累计阅读时长（秒） */
    private long totalSeconds;

    /** 格式化后的阅读时长，如 "2小时35分钟" */
    private String readingTime;
}
