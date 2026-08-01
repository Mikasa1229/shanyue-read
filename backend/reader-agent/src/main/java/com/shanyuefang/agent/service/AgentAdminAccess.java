package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.entity.AgentAdminRole;
import com.shanyuefang.agent.mapper.AgentAdminRoleMapper;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Arrays;

/** Temporary RBAC boundary backed by a server-only administrator allowlist. */
@Component
@RequiredArgsConstructor
public class AgentAdminAccess {
    private final AgentProperties properties;
    private final AgentAdminRoleMapper roleMapper;
    public void require(long userId) {
        boolean allowed = isBootstrapAdmin(userId) || roleMapper.selectById(userId) != null;
        if (!allowed) throw new BusinessException(ResultCode.FORBIDDEN, "Agent administrator permission required");
    }
    public void requireAdmin(long userId) {
        AgentAdminRole role = roleMapper.selectById(userId);
        if (!isBootstrapAdmin(userId) && (role == null || !"ADMIN".equals(role.getRoleCode()))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Agent administrator role required");
        }
    }
    private boolean isBootstrapAdmin(long userId) {
        return Arrays.stream(properties.getAdminUserIds().split(",")).map(String::trim)
                .anyMatch(value -> value.equals(String.valueOf(userId)));
    }
}
