package com.shanyuefang.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应 VO
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** Sa-Token 颁发的 Token */
    private String token;

    /** 用户基本信息 */
    private UserVO userInfo;
}
