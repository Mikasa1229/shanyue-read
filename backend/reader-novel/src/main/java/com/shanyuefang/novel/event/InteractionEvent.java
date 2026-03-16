package com.shanyuefang.novel.event;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 互动事件（从 reader.topic 消费，与 reader-interaction 的 InteractionEvent 结构一致）
 */
@Data
@NoArgsConstructor
public class InteractionEvent {

    private Long targetId;

    /** 1=小说 2=点评 */
    private Integer targetType;

    /** 1=点赞 2=收藏 */
    private Integer action;

    /** LIKED / UNLIKED / FAVORITED / UNFAVORITED */
    private String eventType;

    private Long userId;
}
