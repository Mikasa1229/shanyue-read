package com.shanyuefang.agent.service.impl;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.dto.SaveShelfGroupDTO;
import com.shanyuefang.agent.domain.entity.AgentShelfGroup;
import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.agent.mapper.AgentShelfGroupMapper;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.R;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShelfGroupServiceImplTest {
    @Test
    void rejectsManualGroupForWorkOutsideRequestingUsersShelf() {
        NovelShelfFeignClient shelfClient = mock(NovelShelfFeignClient.class);
        AgentShelfGroupMapper mapper = mock(AgentShelfGroupMapper.class);
        when(shelfClient.list(any(), anyLong())).thenReturn(R.ok(List.of(Map.of("canonicalBookId", 8L))));
        ShelfGroupServiceImpl service = service(shelfClient, mapper);

        SaveShelfGroupDTO dto = new SaveShelfGroupDTO();
        dto.setCanonicalBookId(9L);
        dto.setGroupCode("FOLLOWING");

        assertThrows(BusinessException.class, () -> service.save(1L, dto));
        verify(mapper, never()).insert(any(AgentShelfGroup.class));
    }

    @Test
    void failsClosedWhenPrivateShelfProjectionIsUnavailable() {
        NovelShelfFeignClient shelfClient = mock(NovelShelfFeignClient.class);
        AgentShelfGroupMapper mapper = mock(AgentShelfGroupMapper.class);
        when(shelfClient.list(any(), anyLong())).thenThrow(new IllegalStateException("projection unavailable"));
        ShelfGroupServiceImpl service = service(shelfClient, mapper);
        SaveShelfGroupDTO dto = new SaveShelfGroupDTO();
        dto.setCanonicalBookId(8L);
        dto.setGroupCode("WEEKEND");

        assertThrows(BusinessException.class, () -> service.save(1L, dto));
        verify(mapper, never()).insert(any(AgentShelfGroup.class));
    }

    @Test
    void automaticGroupClearsOnlyAUsersManualOverrideAfterOwnershipCheck() {
        NovelShelfFeignClient shelfClient = mock(NovelShelfFeignClient.class);
        AgentShelfGroupMapper mapper = mock(AgentShelfGroupMapper.class);
        when(shelfClient.list(any(), anyLong())).thenReturn(R.ok(List.of(Map.of("canonicalBookId", 8L))));
        ShelfGroupServiceImpl service = service(shelfClient, mapper);
        SaveShelfGroupDTO dto = new SaveShelfGroupDTO();
        dto.setCanonicalBookId(8L);
        dto.setGroupCode("AUTO");

        assertDoesNotThrow(() -> service.save(1L, dto));
        verify(mapper).delete(any());
    }

    private ShelfGroupServiceImpl service(NovelShelfFeignClient shelfClient, AgentShelfGroupMapper mapper) {
        AgentProperties properties = new AgentProperties();
        properties.setInternalToken("test-token");
        return new ShelfGroupServiceImpl(shelfClient, properties, mapper);
    }
}
