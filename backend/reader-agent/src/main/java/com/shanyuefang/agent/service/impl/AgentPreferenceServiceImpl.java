package com.shanyuefang.agent.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.dto.SaveAgentPreferenceDTO;
import com.shanyuefang.agent.domain.entity.AgentMessage;
import com.shanyuefang.agent.domain.entity.AgentSession;
import com.shanyuefang.agent.domain.entity.UserAgentPreference;
import com.shanyuefang.agent.domain.vo.UserAgentPreferenceVO;
import com.shanyuefang.agent.mapper.AgentMessageMapper;
import com.shanyuefang.agent.mapper.AgentSessionMapper;
import com.shanyuefang.agent.mapper.UserAgentPreferenceMapper;
import com.shanyuefang.agent.service.AgentPreferenceService;
import com.shanyuefang.agent.service.ProfileVectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentPreferenceServiceImpl implements AgentPreferenceService {
    private final UserAgentPreferenceMapper preferenceMapper;
    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final ProfileVectorService profileVectorService;

    @Override
    public UserAgentPreferenceVO get(long userId) {
        UserAgentPreference preference = preferenceMapper.selectById(userId);
        return preference == null ? defaults() : toVO(preference);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAgentPreferenceVO save(long userId, SaveAgentPreferenceDTO dto) {
        UserAgentPreference preference = preferenceMapper.selectById(userId);
        if (preference == null) { preference = new UserAgentPreference(); preference.setUserId(userId); }
        preference.setPreferredGenresJson(write(dto.getPreferredGenres()));
        preference.setAvoidedThemesJson(write(dto.getAvoidedThemes()));
        preference.setSpoilerLevel(dto.getSpoilerLevel());
        preference.setPersonalizationEnabled(Boolean.TRUE.equals(dto.getPersonalizationEnabled()));
        preference.setRetainConversations(Boolean.TRUE.equals(dto.getRetainConversations()));
        preference.setUpdatedAt(LocalDateTime.now());
        if (preferenceMapper.selectById(userId) == null) preferenceMapper.insert(preference); else preferenceMapper.updateById(preference);
        if (Boolean.FALSE.equals(preference.getRetainConversations())) purgeConversations(userId);
        profileVectorService.refreshUserPreference(userId, dto.getPreferredGenres(), dto.getAvoidedThemes(),
                Boolean.TRUE.equals(preference.getPersonalizationEnabled()));
        return toVO(preference);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void erasePersonalData(long userId, boolean eraseConversations) {
        preferenceMapper.deleteById(userId);
        profileVectorService.deleteUserPreference(userId);
        if (!eraseConversations) return;
        purgeConversations(userId);
    }

    private void purgeConversations(long userId) {
        List<AgentSession> sessions = sessionMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AgentSession>lambdaQuery()
                .eq(AgentSession::getUserId, userId));
        for (AgentSession session : sessions) {
            messageMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AgentMessage>lambdaQuery().eq(AgentMessage::getSessionId, session.getId()));
        }
        sessionMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AgentSession>lambdaQuery().eq(AgentSession::getUserId, userId));
    }

    private UserAgentPreferenceVO defaults() {
        UserAgentPreferenceVO value = new UserAgentPreferenceVO(); value.setPreferredGenres(List.of()); value.setAvoidedThemes(List.of());
        value.setSpoilerLevel("STRICT"); value.setPersonalizationEnabled(true); value.setRetainConversations(true); return value;
    }
    private UserAgentPreferenceVO toVO(UserAgentPreference value) {
        UserAgentPreferenceVO result = new UserAgentPreferenceVO(); result.setPreferredGenres(read(value.getPreferredGenresJson()));
        result.setAvoidedThemes(read(value.getAvoidedThemesJson())); result.setSpoilerLevel(value.getSpoilerLevel());
        result.setPersonalizationEnabled(value.getPersonalizationEnabled()); result.setRetainConversations(value.getRetainConversations()); return result;
    }
    private String write(List<String> value) { try { return objectMapper.writeValueAsString(value == null ? List.of() : value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private List<String> read(String value) { try { return objectMapper.readValue(value, new TypeReference<List<String>>() { }); } catch (Exception e) { return List.of(); } }
}
