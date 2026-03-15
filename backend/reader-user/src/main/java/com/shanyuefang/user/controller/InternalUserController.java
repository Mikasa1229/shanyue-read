package com.shanyuefang.user.controller;

import com.shanyuefang.common.result.R;
import com.shanyuefang.user.domain.entity.User;
import com.shanyuefang.user.domain.vo.UserSimpleVO;
import com.shanyuefang.user.service.UserService;
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
}
