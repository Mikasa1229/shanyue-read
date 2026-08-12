package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.common.result.R;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpoilerBoundaryServiceTest {
    @Test
    void clampsClientRequestedChapterToServerOwnedReadingProgress() {
        NovelShelfFeignClient client = mock(NovelShelfFeignClient.class);
        when(client.readingBoundary(anyString(), anyLong(), anyLong())).thenReturn(R.ok(Map.of("currentChapter", 4)));
        AgentProperties properties = new AgentProperties();
        properties.setInternalToken("test-token");

        assertEquals(4, new SpoilerBoundaryService(client, properties).clamp(7L, 9L, 99));
    }

    @Test
    void failsClosedWhenReadingProgressCannotBeVerified() {
        NovelShelfFeignClient client = mock(NovelShelfFeignClient.class);
        when(client.readingBoundary(anyString(), anyLong(), anyLong())).thenThrow(new IllegalStateException("unavailable"));

        assertEquals(0, new SpoilerBoundaryService(client, new AgentProperties()).clamp(7L, 9L, 99));
    }
}
