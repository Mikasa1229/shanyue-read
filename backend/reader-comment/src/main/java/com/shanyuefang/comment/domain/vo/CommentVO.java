package com.shanyuefang.comment.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 点评响应 VO
 */
@Data
public class CommentVO {

    private Long id;
    private Long novelId;
    private Long userId;

    /** 作者昵称（从用户服务 Feign 获取后组装）*/
    private String userNickname;

    /** 作者头像 */
    private String userAvatar;

    /** 书源书名（novelId 为 null 时使用）*/
    private String bookTitle;

    private Long parentId;
    private Long rootId;
    private String content;
    private Integer likeCount;
    private LocalDateTime createdAt;

    /** 是否已被当前用户点赞（需互动服务组装，默认 false）*/
    private Boolean liked = false;
}
