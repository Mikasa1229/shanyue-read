package com.shanyuefang.agent.config;

import com.shanyuefang.agent.service.HashEmbeddingClient;
import com.shanyuefang.agent.service.MilvusCollectionInitializer;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.vectorstore.MilvusVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.agent", name = "milvus-enabled", havingValue = "true")
public class MilvusConfig {
    @Bean
    MilvusServiceClient milvusClient(AgentProperties properties) {
        return new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(properties.getMilvusHost()).withPort(properties.getMilvusPort()).build());
    }

    @Bean
    MilvusVectorStore milvusVectorStore(MilvusServiceClient client, HashEmbeddingClient embeddingClient,
                                        MilvusCollectionInitializer collectionInitializer,
                                        AgentProperties properties) throws Exception {
        collectionInitializer.ensure(client, properties.getMilvusCollection(), embeddingClient.dimensions());
        MilvusVectorStore store = new MilvusVectorStore(client, embeddingClient,
                MilvusVectorStore.MilvusVectorStoreConfig.builder()
                        .withCollectionName(properties.getMilvusCollection())
                        .withEmbeddingDimension(embeddingClient.dimensions())
                        .withMetricType(MetricType.COSINE)
                        .withIndexType(IndexType.IVF_FLAT)
                        .build());
        store.afterPropertiesSet();
        return store;
    }
}
