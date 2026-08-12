package com.shanyuefang.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.dto.AgentModelRouteDTO;
import com.shanyuefang.agent.domain.entity.AgentModelRoute;
import com.shanyuefang.agent.mapper.AgentModelRouteMapper;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class ModelRouteService {
    private final AgentModelRouteMapper mapper; private final AgentProperties properties;
    public List<AgentModelRoute> list() { return mapper.selectList(Wrappers.<AgentModelRoute>lambdaQuery().orderByAsc(AgentModelRoute::getRouteKey)); }
    public String resolve(String routeKey, long userId, String fallback) {
        AgentModelRoute route = mapper.selectOne(Wrappers.<AgentModelRoute>lambdaQuery().eq(AgentModelRoute::getRouteKey, routeKey).last("LIMIT 1"));
        if (route == null || !Boolean.TRUE.equals(route.getEnabled()) || route.getRolloutPercent() == null || route.getRolloutPercent() <= 0) return fallback;
        int bucket = Math.floorMod(Long.hashCode(userId), 100);
        return bucket < route.getRolloutPercent() ? route.getModel() : fallback;
    }
    public AgentModelRoute save(long userId, AgentModelRouteDTO dto) {
        String key = dto.getRouteKey().trim().toUpperCase(Locale.ROOT);
        if (!("FAST".equals(key) || "STRONG".equals(key) || "DEFAULT".equals(key) || "RERANKER".equals(key))) {
            throw new IllegalArgumentException("Unsupported route key");
        }
        AgentModelRoute route = mapper.selectOne(Wrappers.<AgentModelRoute>lambdaQuery().eq(AgentModelRoute::getRouteKey, key));
        if (route == null) { route = new AgentModelRoute(); route.setId(SnowflakeIdUtil.next()); route.setRouteKey(key); route.setCreatedAt(LocalDateTime.now()); }
        route.setModel(dto.getModel().trim()); route.setEnabled(Boolean.TRUE.equals(dto.getEnabled())); route.setRolloutPercent(dto.getRolloutPercent()); route.setUpdatedBy(userId); route.setUpdatedAt(LocalDateTime.now());
        if (mapper.selectById(route.getId()) == null) mapper.insert(route); else mapper.updateById(route); return route;
    }
}
