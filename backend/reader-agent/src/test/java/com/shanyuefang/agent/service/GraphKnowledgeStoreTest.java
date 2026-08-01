package com.shanyuefang.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.mapper.GraphNeighborhoodCacheMapper;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GraphKnowledgeStoreTest {
    @Test
    void timesOutAndOpensCooldownInsteadOfBlockingLightRagQuery() throws Exception {
        ObjectProvider<Driver> provider = mock(ObjectProvider.class);
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        when(provider.getIfAvailable()).thenReturn(driver);
        when(driver.session()).thenReturn(session);
        when(session.executeRead(any())).thenAnswer(invocation -> {
            Thread.sleep(1_000L);
            return List.of("late edge");
        });

        GraphNeighborhoodCacheMapper cache = mock(GraphNeighborhoodCacheMapper.class);
        when(cache.selectOne(any())).thenReturn(null);
        AgentProperties properties = new AgentProperties();
        properties.setNeo4jEnabled(true);
        properties.setNeo4jOperationTimeoutMillis(100);
        properties.setNeo4jFailureCooldownSeconds(2);
        GraphKnowledgeStore graph = new GraphKnowledgeStore(provider, cache, new ObjectMapper(), properties, mock(AgentMetrics.class));

        long started = System.nanoTime();
        assertThat(graph.localNeighborhood(99L, 4, List.of("沈青"), 18)).isEmpty();
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        assertThat(elapsedMillis).isLessThan(700L);

        // The open circuit skips a second blocked Bolt call while relational graph data remains available.
        assertThat(graph.localNeighborhood(99L, 4, List.of("沈青"), 18)).isEmpty();
        verify(session, times(1)).executeRead(any());
    }
}
