package com.shanyuefang.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.dto.AgentModelPricingDTO;
import com.shanyuefang.agent.domain.entity.AgentModelPricing;
import com.shanyuefang.agent.mapper.AgentModelPricingMapper;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Resolves platform cost from an auditable price table while retaining safe config fallback. */
@Service
@RequiredArgsConstructor
public class ModelPricingService {
    private final AgentModelPricingMapper mapper;
    private final AgentProperties properties;

    public List<AgentModelPricing> list() {
        return mapper.selectList(Wrappers.<AgentModelPricing>lambdaQuery()
                .orderByAsc(AgentModelPricing::getProvider).orderByAsc(AgentModelPricing::getModel));
    }

    public long platformCostMicros(String provider, String model, int inputTokens, int outputTokens) {
        AgentModelPricing price = mapper.selectOne(Wrappers.<AgentModelPricing>lambdaQuery()
                .eq(AgentModelPricing::getProvider, normalized(provider))
                .eq(AgentModelPricing::getModel, normalized(model))
                .eq(AgentModelPricing::getEnabled, true).last("LIMIT 1"));
        long inputRate = price == null ? properties.getPlatformInputCostMicrosPerThousand() : price.getInputCostMicrosPerThousand();
        long outputRate = price == null ? properties.getPlatformOutputCostMicrosPerThousand() : price.getOutputCostMicrosPerThousand();
        return Math.round((Math.max(0, inputTokens) * inputRate + Math.max(0, outputTokens) * outputRate) / 1000D);
    }

    public AgentModelPricing save(long userId, AgentModelPricingDTO dto) {
        String provider = normalized(dto.getProvider());
        String model = normalized(dto.getModel());
        AgentModelPricing value = mapper.selectOne(Wrappers.<AgentModelPricing>lambdaQuery()
                .eq(AgentModelPricing::getProvider, provider).eq(AgentModelPricing::getModel, model));
        if (value == null) {
            value = new AgentModelPricing();
            value.setId(SnowflakeIdUtil.next());
            value.setProvider(provider);
            value.setModel(model);
            value.setCreatedAt(LocalDateTime.now());
        }
        value.setInputCostMicrosPerThousand(dto.getInputCostMicrosPerThousand());
        value.setOutputCostMicrosPerThousand(dto.getOutputCostMicrosPerThousand());
        value.setPricingVersion(dto.getPricingVersion().trim());
        value.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        value.setUpdatedBy(userId);
        value.setUpdatedAt(LocalDateTime.now());
        if (mapper.selectById(value.getId()) == null) mapper.insert(value); else mapper.updateById(value);
        return value;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
