package com.shanyuefang.agent.service;

import com.shanyuefang.agent.domain.dto.ChatMessageDTO;
import com.shanyuefang.agent.domain.dto.CreateSessionDTO;
import com.shanyuefang.agent.domain.dto.SaveModelConfigDTO;
import com.shanyuefang.agent.domain.vo.AgentMessageVO;
import com.shanyuefang.agent.domain.vo.AgentReplyVO;
import com.shanyuefang.agent.domain.vo.AgentSessionVO;
import com.shanyuefang.agent.domain.vo.UserModelConfigVO;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface AgentService {
    boolean acquireConversationSlot(long userId, long sessionId, String clientIp);
    void releaseConversationSlot(long sessionId);
    AgentSessionVO createSession(long userId, CreateSessionDTO dto);
    List<AgentSessionVO> listSessions(long userId);
    List<AgentSessionVO> searchSessions(long userId, String keyword);
    List<AgentMessageVO> listMessages(long userId, long sessionId);
    Map<String, Object> exportSession(long userId, long sessionId);
    void deleteSession(long userId, long sessionId);
    AgentReplyVO chat(long userId, long sessionId, ChatMessageDTO dto);
    AgentReplyVO streamChat(long userId, long sessionId, ChatMessageDTO dto, Consumer<String> onDelta);
    UserModelConfigVO saveModelConfig(long userId, SaveModelConfigDTO dto);
    List<UserModelConfigVO> listModelConfigs(long userId);
    UserModelConfigVO setModelConfigEnabled(long userId, long configId, boolean enabled);
    void testModelConfig(long userId, long configId);
    void deleteModelConfig(long userId, long configId);
}
