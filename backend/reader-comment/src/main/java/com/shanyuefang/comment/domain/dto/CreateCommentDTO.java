package com.shanyuefang.comment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交点评 DTO
 */
@Data
public class CreateCommentDTO {

    @NotNull(message = "小说 ID 不能为空")
    private Long novelId;

    @NotBlank(message = "点评内容不能为空")
    @Size(min = 1, max = 1000, message = "点评内容长度为 1~1000 字")
    private String content;

    /** 回复的评论 ID，为 null 则为根评论 */
    private Long parentId;
}
