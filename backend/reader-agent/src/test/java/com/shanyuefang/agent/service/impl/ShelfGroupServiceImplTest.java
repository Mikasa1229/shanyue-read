package com.shanyuefang.agent.service.impl;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.dto.SaveShelfGroupDTO;
import com.shanyuefang.agent.domain.entity.AgentShelfGroup;
import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.agent.mapper.AgentShelfGroupMapper;
import com.shanyuefang.agent.mapper.KnowledgeVectorProfileMapper;
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
        dto.setGroupName("玄幻与仙侠");

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
        dto.setGroupName("周末书单");

        assertThrows(BusinessException.class, () -> service.save(1L, dto));
        verify(mapper, never()).insert(any(AgentShelfGroup.class));
    }

    @Test
    void savesDirectoryOnlyAfterOwnershipCheck() {
        NovelShelfFeignClient shelfClient = mock(NovelShelfFeignClient.class);
        AgentShelfGroupMapper mapper = mock(AgentShelfGroupMapper.class);
        when(shelfClient.list(any(), anyLong())).thenReturn(R.ok(List.of(Map.of("canonicalBookId", 8L))));
        ShelfGroupServiceImpl service = service(shelfClient, mapper);
        SaveShelfGroupDTO dto = new SaveShelfGroupDTO();
        dto.setCanonicalBookId(8L);
        dto.setGroupName("仙侠作品");

        assertDoesNotThrow(() -> service.save(1L, dto));
        verify(mapper).insert(any(AgentShelfGroup.class));
    }

    private ShelfGroupServiceImpl service(NovelShelfFeignClient shelfClient, AgentShelfGroupMapper mapper) {
        AgentProperties properties = new AgentProperties();
        properties.setInternalToken("test-token");
        return new ShelfGroupServiceImpl(shelfClient, properties, mapper, mock(KnowledgeVectorProfileMapper.class));
    }
}
