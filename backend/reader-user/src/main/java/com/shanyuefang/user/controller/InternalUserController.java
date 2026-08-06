package com.shanyuefang.user.controller;

import com.shanyuefang.common.result.R;
import com.shanyuefang.user.domain.entity.User;
import com.shanyuefang.user.domain.vo.UserSimpleVO;
import com.shanyuefang.user.service.UserService;
import com.shanyuefang.user.service.CreditService;
import com.shanyuefang.user.domain.dto.CreditOperationDTO;
import com.shanyuefang.user.domain.vo.UserCreditVO;
import com.shanyuefang.user.domain.vo.LevelActionResultVO;
import com.shanyuefang.user.domain.dto.LevelActionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内部接口：仅供微服务间 Feign 调用，不对外暴露（Gateway 不路由 /internal/**)
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;
    private final CreditService creditService;

    /**
     * 批量查询用户简要信息
     *
     * @param ids 用户 ID 列表
     * @return Map<userId, UserSimpleVO>
     */
    @GetMapping("/batch")
    public R<Map<Long, UserSimpleVO>> batchGetUsers(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.ok(Collections.emptyMap());
        }
        // 利用 ServiceImpl 的 listByIds 批量查询
        List<User> users = userService.listByIds(ids);
        Map<Long, UserSimpleVO> result = users.stream().collect(Collectors.toMap(
                User::getId,
                u -> {
                    UserSimpleVO vo = new UserSimpleVO();
                    BeanUtils.copyProperties(u, vo);
                    return vo;
                }
        ));
        return R.ok(result);
    }
    @GetMapping("/credits")
    public R<UserCreditVO> getCredits(@RequestParam Long userId) {
        return R.ok(creditService.getCredits(userId));
    }

    @org.springframework.web.bind.annotation.PostMapping("/credits/freeze")
    public R<Void> freezeCredits(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody CreditOperationDTO dto) {
        creditService.freeze(dto);
        return R.ok();
    }

    @org.springframework.web.bind.annotation.PostMapping("/credits/grant")
    public R<Void> grantCredits(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody CreditOperationDTO dto) {
        creditService.grant(dto);
        return R.ok();
    }

    @org.springframework.web.bind.annotation.PostMapping("/credits/settle")
    public R<Void> settleCredits(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody CreditOperationDTO dto) {
        creditService.settle(dto);
        return R.ok();
    }

    @org.springframework.web.bind.annotation.PostMapping("/credits/refund")
    public R<Void> refundCredits(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody CreditOperationDTO dto) {
        creditService.refund(dto);
        return R.ok();
    }

    @org.springframework.web.bind.annotation.PostMapping("/level/verified-reading")
    public R<LevelActionResultVO> recordVerifiedReading(@RequestParam Long userId, @RequestParam int seconds) {
        return R.ok(userService.recordVerifiedReading(userId, seconds));
    }

    @org.springframework.web.bind.annotation.PostMapping("/level/action")
    public R<LevelActionResultVO> recordVerifiedAction(@RequestParam Long userId,
                                                        @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody LevelActionDTO dto) {
        return R.ok(userService.recordLevelAction(userId, dto));
    }
}
