package com.shanyuefang.agent.service;

import com.shanyuefang.agent.domain.dto.SaveAgentPreferenceDTO;
import com.shanyuefang.agent.domain.vo.UserAgentPreferenceVO;

public interface AgentPreferenceService {
    UserAgentPreferenceVO get(long userId);
    UserAgentPreferenceVO save(long userId, SaveAgentPreferenceDTO dto);
    void erasePersonalData(long userId, boolean eraseConversations);
}
