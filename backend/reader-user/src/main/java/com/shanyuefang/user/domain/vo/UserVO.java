package com.shanyuefang.user.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息响应 VO
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer status;
    private LocalDateTime createdAt;
}
