package com.shanyuefang.comment.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 点评事件（发送到 RabbitMQ，供小说服务消费更新 comment_count）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentEvent implements Serializable {

    /** 点评 ID */
    private Long commentId;

    /** 所属小说 ID */
    private Long novelId;

    /** 事件类型：CREATED / DELETED */
    private String action;
}
