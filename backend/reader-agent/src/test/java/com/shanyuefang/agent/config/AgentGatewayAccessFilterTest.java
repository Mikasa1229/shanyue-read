package com.shanyuefang.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentGatewayAccessFilterTest {
    @Test
    void rejectsDirectAgentRequestWithoutGatewaySecret() throws Exception {
        AgentProperties properties = new AgentProperties(); properties.setGatewayToken("gateway-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/sessions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AgentGatewayAccessFilter(properties).doFilter(request, response, new MockFilterChain());

        assertEquals(404, response.getStatus());
    }

    @Test
    void acceptsGatewayAuthenticatedAgentRequest() throws Exception {
        AgentProperties properties = new AgentProperties(); properties.setGatewayToken("gateway-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/sessions");
        request.addHeader("X-Agent-Gateway-Token", "gateway-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AgentGatewayAccessFilter(properties).doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void failsClosedWhenDeploymentSecretIsMissing() throws Exception {
        AgentProperties properties = new AgentProperties();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/infrastructure");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AgentGatewayAccessFilter(properties).doFilter(request, response, new MockFilterChain());

        assertEquals(404, response.getStatus());
    }
}
