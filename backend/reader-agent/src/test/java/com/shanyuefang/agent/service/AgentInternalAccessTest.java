package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentInternalAccessTest {
    @Test
    void acceptsOnlyTheConfiguredInternalToken() {
        AgentProperties properties = new AgentProperties();
        properties.setInternalToken("test-internal-token");
        AgentInternalAccess access = new AgentInternalAccess(properties);

        assertDoesNotThrow(() -> access.require("test-internal-token"));
        assertThrows(BusinessException.class, () -> access.require("wrong-token"));
    }

    @Test
    void rejectsRequestsWhenNoInternalCredentialIsConfigured() {
        assertThrows(BusinessException.class, () -> new AgentInternalAccess(new AgentProperties()).require("anything"));
    }
}
