package com.shanyuefang.user.controller;

import com.shanyuefang.common.result.R;
import com.shanyuefang.user.config.MinioProperties;
import com.shanyuefang.user.domain.dto.LevelActionDTO;
import com.shanyuefang.user.domain.dto.UpdatePasswordDTO;
import com.shanyuefang.user.domain.dto.UpdateUserDTO;
import com.shanyuefang.user.domain.vo.LevelActionResultVO;
import com.shanyuefang.user.domain.vo.UserLevelVO;
import com.shanyuefang.user.domain.vo.UserVO;
import com.shanyuefang.user.service.UserService;
import com.shanyuefang.user.service.CreditService;
import com.shanyuefang.user.domain.vo.UserCreditVO;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Tag(name = "用户接口", description = "获取 / 更新当前用户信息")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CreditService creditService;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

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

    @Operation(summary = "上传头像")
    @PostMapping("/me/avatar")
    public R<Map<String, String>> uploadAvatar(@RequestHeader("X-User-Id") Long userId,
                                               @RequestParam("file") MultipartFile file) throws Exception {
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "avatar";
        String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : ".jpg";
        // 对象路径：avatars/<uuid><ext>
        String objectName = "avatars/" + UUID.randomUUID().toString().replace("-", "") + ext;

        // 上传到 MinIO
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType() != null ? file.getContentType() : "image/jpeg")
                        .build()
        );

        // 拼接公开访问 URL：<publicUrl>/<bucket>/<objectName>
        String url = minioProperties.getPublicUrl() + "/" + minioProperties.getBucket() + "/" + objectName;

        // 同步更新用户头像字段
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setAvatar(url);
        userService.updateUser(userId, dto);

        return R.ok(Map.of("url", url));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/me/password")
    public R<Void> updatePassword(@RequestHeader("X-User-Id") Long userId,
                                  @Valid @RequestBody UpdatePasswordDTO dto) {
        userService.updatePassword(userId, dto);
        return R.ok();
    }

    @Operation(summary = "获取当前用户等级与每日任务")
    @GetMapping("/me/level")
    public R<UserLevelVO> getMyLevel(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(userService.getUserLevel(userId));
    }

    @Operation(summary = "记录等级行为（阅读/打卡/点评/评分）")
    @PostMapping("/me/level/action")
    public R<LevelActionResultVO> recordLevelAction(@RequestHeader("X-User-Id") Long userId,
                                                     @Valid @RequestBody LevelActionDTO dto) {
        return R.ok(userService.recordLevelAction(userId, dto));
    }
    @GetMapping("/me/credits")
    public R<UserCreditVO> getCredits(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(creditService.getCredits(userId));
    }
}
