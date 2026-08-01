package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.entity.KnowledgeVectorProfile;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.MilvusVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps profile collections separate so unrelated embeddings never share a search namespace. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileVectorStore {
    private final ObjectProvider<MilvusServiceClient> clientProvider;
    private final HashEmbeddingClient embeddingClient;
    private final MilvusCollectionInitializer collectionInitializer;
    private final AgentProperties properties;
    private final Map<String, MilvusVectorStore> stores = new ConcurrentHashMap<>();

    public void upsert(KnowledgeVectorProfile profile) {
        MilvusVectorStore store = store(profile.getProfileType());
        if (store == null) return;
        try {
            Document document = new Document(profile.getProfileType() + ":" + profile.getSubjectId(), profile.getContent(), Map.of(
                    "profileType", profile.getProfileType(),
                    "subjectId", profile.getSubjectId(),
                    "canonicalBookId", profile.getCanonicalBookId() == null ? 0L : profile.getCanonicalBookId(),
                    "modelVersion", profile.getModelVersion()
            ));
            document.setEmbedding(readVector(profile.getEmbeddingJson()));
            store.add(List.of(document));
        } catch (Exception exception) {
            log.warn("Milvus profile projection failed; PostgreSQL profile remains available: type={}, subject={}",
                    profile.getProfileType(), profile.getSubjectId(), exception);
        }
    }

    public void delete(String profileType, long subjectId) {
        MilvusVectorStore store = store(profileType);
        if (store == null) return;
        try {
            store.delete(List.of(profileType + ":" + subjectId));
        } catch (Exception exception) {
            log.warn("Milvus profile projection delete failed: type={}, subject={}", profileType, subjectId, exception);
        }
    }

    private MilvusVectorStore store(String profileType) {
        if (!properties.isMilvusEnabled()) return null;
        MilvusServiceClient client = clientProvider.getIfAvailable();
        if (client == null) return null;
        try {
            return stores.computeIfAbsent(profileType, ignored -> create(client, collection(profileType)));
        } catch (Exception exception) {
            log.warn("Milvus profile collection is unavailable; keeping PostgreSQL profile active: type={}", profileType, exception);
            return null;
        }
    }

    private MilvusVectorStore create(MilvusServiceClient client, String collection) {
        try {
            collectionInitializer.ensure(client, collection, embeddingClient.dimensions());
            MilvusVectorStore store = new MilvusVectorStore(client, embeddingClient,
                    MilvusVectorStore.MilvusVectorStoreConfig.builder()
                            .withCollectionName(collection)
                            .withEmbeddingDimension(embeddingClient.dimensions())
                            .withMetricType(MetricType.COSINE)
                            .withIndexType(IndexType.IVF_FLAT)
                            .build());
            store.afterPropertiesSet();
            return store;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize Milvus profile collection " + collection, exception);
        }
    }

    private String collection(String profileType) {
        return switch (profileType) {
            case "BOOK" -> properties.getBookProfileCollection();
            case "CHARACTER" -> properties.getCharacterProfileCollection();
            case "EVENT" -> properties.getEventProfileCollection();
            case "COMMUNITY" -> properties.getCommunityProfileCollection();
            case "USER_PREFERENCE" -> properties.getUserPreferenceProfileCollection();
            default -> throw new IllegalArgumentException("Unsupported vector profile type: " + profileType);
        };
    }

    @SuppressWarnings("unchecked")
    private List<Double> readVector(String value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value, List.class);
        } catch (Exception exception) {
            return List.of();
        }
    }
}
