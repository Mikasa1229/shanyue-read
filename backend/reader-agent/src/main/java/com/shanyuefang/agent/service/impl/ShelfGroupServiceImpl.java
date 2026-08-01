package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.dto.SaveShelfGroupDTO;
import com.shanyuefang.agent.domain.entity.AgentShelfGroup;
import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.agent.mapper.AgentShelfGroupMapper;
import com.shanyuefang.agent.service.ShelfGroupService;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.R;
import com.shanyuefang.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShelfGroupServiceImpl implements ShelfGroupService {
    private final NovelShelfFeignClient shelfClient;
    private final AgentProperties properties;
    private final AgentShelfGroupMapper groupMapper;

    @Override
    public List<Map<String, Object>> groups(long userId) {
        Map<Long, String> pinned = groupMapper.selectList(Wrappers.<AgentShelfGroup>lambdaQuery().eq(AgentShelfGroup::getUserId, userId)).stream()
                .collect(Collectors.toMap(AgentShelfGroup::getCanonicalBookId, AgentShelfGroup::getGroupCode));
        try {
            R<List<Map<String, Object>>> result = shelfClient.list(properties.getInternalToken(), userId);
            return (result == null || result.getData() == null ? List.<Map<String, Object>>of() : result.getData()).stream().map(book -> {
                Long id = number(book.get("canonicalBookId"));
                String group = id == null ? "AUTO" : pinned.getOrDefault(id, automaticGroup(book));
                return Map.<String, Object>of("canonicalBookId", id == null ? 0L : id, "title", String.valueOf(book.getOrDefault("bookName", "Untitled work")), "groupCode", group, "pinned", pinned.containsKey(id));
            }).toList();
        } catch (Exception ignored) { return List.of(); }
    }

    @Override
    public void save(long userId, SaveShelfGroupDTO dto) {
        if (dto.getCanonicalBookId() == null || dto.getCanonicalBookId() <= 0) throw new IllegalArgumentException("Invalid canonical book ID");
        if (!ownsShelfBook(userId, dto.getCanonicalBookId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Book is not on the requesting user's shelf");
        }
        if ("AUTO".equals(dto.getGroupCode())) { groupMapper.delete(Wrappers.<AgentShelfGroup>lambdaQuery().eq(AgentShelfGroup::getUserId, userId).eq(AgentShelfGroup::getCanonicalBookId, dto.getCanonicalBookId())); return; }
        AgentShelfGroup value = groupMapper.selectOne(Wrappers.<AgentShelfGroup>lambdaQuery().eq(AgentShelfGroup::getUserId, userId).eq(AgentShelfGroup::getCanonicalBookId, dto.getCanonicalBookId()));
        if (value == null) { value = new AgentShelfGroup(); value.setUserId(userId); value.setCanonicalBookId(dto.getCanonicalBookId()); value.setCreatedAt(LocalDateTime.now()); }
        value.setGroupCode(dto.getGroupCode()); value.setUpdatedAt(LocalDateTime.now());
        if (groupMapper.selectOne(Wrappers.<AgentShelfGroup>lambdaQuery().eq(AgentShelfGroup::getUserId, userId).eq(AgentShelfGroup::getCanonicalBookId, dto.getCanonicalBookId())) == null) groupMapper.insert(value);
        else groupMapper.update(value, Wrappers.<AgentShelfGroup>lambdaUpdate().eq(AgentShelfGroup::getUserId, userId).eq(AgentShelfGroup::getCanonicalBookId, dto.getCanonicalBookId()));
    }

    private String automaticGroup(Map<String, Object> book) {
        if (book.get("lastReadAt") != null) return "FOLLOWING";
        Integer total = integer(book.get("totalChapters"));
        return total != null && total <= 30 ? "SHORT_SESSION" : "RESTART";
    }
    private boolean ownsShelfBook(long userId, long canonicalBookId) {
        try {
            R<List<Map<String, Object>>> result = shelfClient.list(properties.getInternalToken(), userId);
            return result != null && result.getData() != null && result.getData().stream()
                    .map(book -> number(book.get("canonicalBookId"))).anyMatch(id -> id != null && id == canonicalBookId);
        } catch (Exception ignored) { return false; }
    }
    private Long number(Object value) { try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return null; } }
    private Integer integer(Object value) { try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return null; } }
}
