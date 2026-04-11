package com.shanyuefang.comment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交点评 DTO
 * novelId 和 bookTitle 二选一：
 * - 系统书库书评：填 novelId
 * - 书源书籍书评：填 bookTitle（novelId 可为 null）
 */
@Data
public class CreateCommentDTO {

    /** 小说 ID，书源书籍书评时可为 null */
    private Long novelId;

    /** 书源书名，novelId 为 null 时使用 */
    private String bookTitle;

    /** 书源 ID（书源书籍点评时使用） */
    private Long sourceId;

    /** 书源书籍 URL（用于跳转详情） */
    private String bookUrl;

    /** 书源作者 */
    private String bookAuthor;

    /** 书源封面 URL */
    private String bookCoverUrl;

    /** 书源简介 */
    private String bookIntro;

    @NotBlank(message = "点评内容不能为空")
    @Size(min = 1, max = 1000, message = "点评内容长度为 1~1000 字")
    private String content;

    @Min(value = 1, message = "评分范围为 1~5")
    @Max(value = 5, message = "评分范围为 1~5")
    private Integer score;

    /** 回复的评论 ID，为 null 则为根评论 */
    private Long parentId;
}
