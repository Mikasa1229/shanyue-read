package com.shanyuefang.comment.feign.vo;

import lombok.Data;

/**
 * 用户简要信息 VO（Feign 响应反序列化用）
 */
@Data
public class UserSimpleVO {
    private Long id;
    private String nickname;
    private String avatar;
}
