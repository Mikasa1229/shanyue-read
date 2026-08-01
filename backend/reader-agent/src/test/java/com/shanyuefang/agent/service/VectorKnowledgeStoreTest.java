package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.MilvusVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VectorKnowledgeStoreTest {
    @Test
    void timesOutAndOpensCooldownInsteadOfBlockingTheRequest() throws Exception {
        ObjectProvider<MilvusVectorStore> provider = mock(ObjectProvider.class);
        MilvusVectorStore store = mock(MilvusVectorStore.class);
        when(provider.getIfAvailable()).thenReturn(store);
        when(store.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(1_000L);
            return List.of(new Document("late", "late result", java.util.Map.of()));
        });

        AgentProperties properties = new AgentProperties();
        properties.setMilvusEnabled(true);
        properties.setMilvusOperationTimeoutMillis(100);
        properties.setMilvusFailureCooldownSeconds(2);
        VectorKnowledgeStore vectors = new VectorKnowledgeStore(provider, properties, mock(AgentMetrics.class));

        long started = System.nanoTime();
        assertThat(vectors.search("question", 3)).isEmpty();
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        assertThat(elapsedMillis).isLessThan(700L);

        // The open circuit skips the dead client immediately and prevents a second blocked call.
        assertThat(vectors.search("question", 3)).isEmpty();
        verify(store, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void successfulSearchClearsPreviousCooldown() {
        ObjectProvider<MilvusVectorStore> provider = mock(ObjectProvider.class);
        MilvusVectorStore store = mock(MilvusVectorStore.class);
        when(provider.getIfAvailable()).thenReturn(store);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("1", "visible", java.util.Map.of())));

        AgentProperties properties = new AgentProperties();
        properties.setMilvusEnabled(true);
        properties.setMilvusOperationTimeoutMillis(500);
        VectorKnowledgeStore vectors = new VectorKnowledgeStore(provider, properties, mock(AgentMetrics.class));

        assertThat(vectors.search("question", 1)).extracting(Document::getContent).containsExactly("visible");
        verify(store).similaritySearch(any(SearchRequest.class));
    }
}
