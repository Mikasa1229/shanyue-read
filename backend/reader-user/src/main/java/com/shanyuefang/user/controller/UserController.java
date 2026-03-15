package com.shanyuefang.user.controller;

import com.shanyuefang.common.result.R;
import com.shanyuefang.user.domain.dto.UpdatePasswordDTO;
import com.shanyuefang.user.domain.dto.UpdateUserDTO;
import com.shanyuefang.user.domain.vo.UserVO;
import com.shanyuefang.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户接口", description = "获取 / 更新当前用户信息")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Gateway 鉴权通过后将 userId 注入 X-User-Id Header，
     * 下游服务直接从 Header 读取，无需再查 Redis 验 Token。
     */
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public R<UserVO> getCurrentUser(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(userService.getCurrentUser(userId));
    }

    @Operation(summary = "更新用户信息（昵称、头像）")
    @PutMapping("/me")
    public R<UserVO> updateUser(@RequestHeader("X-User-Id") Long userId,
                                @Valid @RequestBody UpdateUserDTO dto) {
        return R.ok(userService.updateUser(userId, dto));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/me/password")
    public R<Void> updatePassword(@RequestHeader("X-User-Id") Long userId,
                                  @Valid @RequestBody UpdatePasswordDTO dto) {
        userService.updatePassword(userId, dto);
        return R.ok();
    }
}
