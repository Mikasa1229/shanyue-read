package com.shanyuefang.user.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户信息 DTO
 */
@Data
public class UpdateUserDTO {

    @Size(max = 64, message = "昵称最多 64 个字符")
    private String nickname;

    @Size(max = 256, message = "头像 URL 过长")
    private String avatar;
}
