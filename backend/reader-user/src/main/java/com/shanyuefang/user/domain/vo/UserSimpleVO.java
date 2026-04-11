package com.shanyuefang.user.domain.vo;

import lombok.Data;

/**
 * 用户简要信息（供内部 Feign 调用使用）
 */
@Data
public class UserSimpleVO {
    private Long id;
    private String nickname;
    private String avatar;
    private Long expTotal;
}
