package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.entity.AgentModelPricing;
import com.shanyuefang.agent.mapper.AgentModelPricingMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelPricingServiceTest {
    @Test
    void usesConfiguredFallbackOnlyWhenNoEnabledPriceExists() {
        AgentProperties properties = new AgentProperties();
        properties.setPlatformInputCostMicrosPerThousand(1000);
        properties.setPlatformOutputCostMicrosPerThousand(3000);
        AgentModelPricingMapper mapper = mock(AgentModelPricingMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);

        assertEquals(8L, new ModelPricingService(mapper, properties).platformCostMicros("deepseek", "chat", 2, 2));
    }

    @Test
    void usesVersionedPlatformPriceForTheSelectedProviderAndModel() {
        AgentProperties properties = new AgentProperties();
        AgentModelPricing price = new AgentModelPricing();
        price.setInputCostMicrosPerThousand(2000L);
        price.setOutputCostMicrosPerThousand(4000L);
        AgentModelPricingMapper mapper = mock(AgentModelPricingMapper.class);
        when(mapper.selectOne(any())).thenReturn(price);

        assertEquals(10L, new ModelPricingService(mapper, properties).platformCostMicros("deepseek", "chat", 3, 1));
    }
}
