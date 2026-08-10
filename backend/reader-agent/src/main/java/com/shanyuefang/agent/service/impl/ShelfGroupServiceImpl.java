package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.dto.SaveShelfGroupDTO;
import com.shanyuefang.agent.domain.entity.AgentShelfGroup;
import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.agent.mapper.AgentShelfGroupMapper;
import com.shanyuefang.agent.mapper.KnowledgeVectorProfileMapper;
import com.shanyuefang.agent.domain.entity.KnowledgeVectorProfile;
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
    private final KnowledgeVectorProfileMapper profileMapper;

    @Override
    public List<Map<String, Object>> groups(long userId) {
        Map<Long, AgentShelfGroup> pinned = groupMapper.selectList(Wrappers.<AgentShelfGroup>lambdaQuery().eq(AgentShelfGroup::getUserId, userId)).stream()
                .collect(Collectors.toMap(AgentShelfGroup::getCanonicalBookId, value -> value));
        try {
            R<List<Map<String, Object>>> result = shelfClient.list(properties.getInternalToken(), userId);
            return (result == null || result.getData() == null ? List.<Map<String, Object>>of() : result.getData()).stream().map(book -> {
                Long id = number(book.get("canonicalBookId"));
                AgentShelfGroup override = id == null ? null : pinned.get(id);
                String groupName = override != null && override.getGroupName() != null ? override.getGroupName() : automaticDirectory(id, book);
                return Map.<String, Object>of("canonicalBookId", id == null ? 0L : id,
                        "title", String.valueOf(book.getOrDefault("bookName", "未命名作品")),
                        "author", String.valueOf(book.getOrDefault("author", "")), "groupName", groupName,
                        "reason", override == null ? "根据作品画像自动归类" : "你手动指定的目录", "pinned", override != null);
            }).toList();
        } catch (Exception ignored) { return List.of(); }
    }

    @Override
    public void save(long userId, SaveShelfGroupDTO dto) {
        if (dto.getCanonicalBookId() == null || dto.getCanonicalBookId() <= 0) throw new IllegalArgumentException("Invalid canonical book ID");
        if (!ownsShelfBook(userId, dto.getCanonicalBookId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Book is not on the requesting user's shelf");
        }
        AgentShelfGroup value = groupMapper.selectOne(Wrappers.<AgentShelfGroup>lambdaQuery().eq(AgentShelfGroup::getUserId, userId).eq(AgentShelfGroup::getCanonicalBookId, dto.getCanonicalBookId()));
        if (value == null) { value = new AgentShelfGroup(); value.setUserId(userId); value.setCanonicalBookId(dto.getCanonicalBookId()); value.setCreatedAt(LocalDateTime.now()); }
        value.setGroupCode("DIRECTORY"); value.setGroupName(dto.getGroupName().trim()); value.setUpdatedAt(LocalDateTime.now());
        if (groupMapper.selectOne(Wrappers.<AgentShelfGroup>lambdaQuery().eq(AgentShelfGroup::getUserId, userId).eq(AgentShelfGroup::getCanonicalBookId, dto.getCanonicalBookId())) == null) groupMapper.insert(value);
        else groupMapper.update(value, Wrappers.<AgentShelfGroup>lambdaUpdate().eq(AgentShelfGroup::getUserId, userId).eq(AgentShelfGroup::getCanonicalBookId, dto.getCanonicalBookId()));
    }

    private String automaticDirectory(Long canonicalBookId, Map<String, Object> book) {
        String profile = "";
        if (canonicalBookId != null) {
            KnowledgeVectorProfile value = profileMapper.selectOne(Wrappers.<KnowledgeVectorProfile>lambdaQuery()
                    .eq(KnowledgeVectorProfile::getProfileType, "BOOK")
                    .eq(KnowledgeVectorProfile::getCanonicalBookId, canonicalBookId).isNull(KnowledgeVectorProfile::getDeletedAt));
            if (value != null && value.getContent() != null) profile = value.getContent();
        }
        String text = (String.valueOf(book.getOrDefault("bookName", "")) + " " + profile).toLowerCase();
        if (containsAny(text, "玄幻", "修仙", "仙侠", "剑", "宗门", "武道")) return "玄幻与仙侠";
        if (containsAny(text, "悬疑", "推理", "案件", "侦探", "谜案")) return "悬疑与推理";
        if (containsAny(text, "科幻", "星际", "未来", "机甲", "末日")) return "科幻与未来";
        if (containsAny(text, "言情", "爱情", "婚姻", "都市")) return "都市与情感";
        return "待整理作品";
    }
    private boolean containsAny(String value, String... terms) { return java.util.Arrays.stream(terms).anyMatch(value::contains); }
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
