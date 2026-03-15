package com.shanyuefang.interaction.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 互动事件（发到 RabbitMQ，供小说/点评服务消费更新计数）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InteractionEvent implements Serializable {

    private Long targetId;

    /** 1=小说 2=点评 */
    private Integer targetType;

    /** 1=点赞 2=收藏 */
    private Integer action;

    /** LIKED / UNLIKED / FAVORITED / UNFAVORITED */
    private String eventType;

    private Long userId;
}
