package com.shanyuefang.agent.service;

import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.index.DescribeIndexParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Spring AI 0.8 creates an index by first calling DescribeIndex, but Milvus 2.4
 * reports a missing index as an exception. Create the standard collection/index
 * explicitly before giving it to Spring AI so first-use projections are reliable.
 */
@Slf4j
@Component
public class MilvusCollectionInitializer {
    private static final String VECTOR_FIELD = "embedding";

    public void ensure(MilvusServiceClient client, String collection, int dimensions) {
        R<Boolean> exists = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collection).build());
        if (exists.getException() != null) {
            throw new IllegalStateException("Could not check Milvus collection " + collection, exists.getException());
        }
        if (!Boolean.TRUE.equals(exists.getData())) {
            R<?> created = client.createCollection(CreateCollectionParam.newBuilder()
                    .withCollectionName(collection)
                    .withDescription("Reader Agent Spring AI vectors")
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .withShardsNum(2)
                    .addFieldType(FieldType.newBuilder().withName("doc_id").withDataType(DataType.VarChar)
                            .withMaxLength(36).withPrimaryKey(true).build())
                    .addFieldType(FieldType.newBuilder().withName("content").withDataType(DataType.VarChar)
                            .withMaxLength(65535).build())
                    .addFieldType(FieldType.newBuilder().withName("metadata").withDataType(DataType.JSON).build())
                    .addFieldType(FieldType.newBuilder().withName(VECTOR_FIELD).withDataType(DataType.FloatVector)
                            .withDimension(dimensions).build())
                    .build());
            if (created.getException() != null) {
                throw new IllegalStateException("Could not create Milvus collection " + collection, created.getException());
            }
        }
        R<?> described;
        try {
            described = client.describeIndex(DescribeIndexParam.newBuilder().withCollectionName(collection).build());
        } catch (Exception exception) {
            // Milvus 2.4 throws instead of returning an empty index list on first use.
            described = R.success();
        }
        if (described.getException() != null || described.getData() == null) {
            R<?> index = client.createIndex(CreateIndexParam.newBuilder()
                    .withCollectionName(collection).withFieldName(VECTOR_FIELD)
                    .withIndexType(IndexType.IVF_FLAT).withMetricType(MetricType.COSINE)
                    .withExtraParam("{\"nlist\":128}")
                    .withSyncMode(true).build());
            if (index.getException() != null && !index.getException().getMessage().toLowerCase().contains("already exist")) {
                throw new IllegalStateException("Could not create Milvus index for " + collection, index.getException());
            }
        }
        R<?> loaded = client.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collection).withSyncLoad(true).build());
        if (loaded.getException() != null) {
            throw new IllegalStateException("Could not load Milvus collection " + collection, loaded.getException());
        }
        log.debug("Milvus collection ready: {}", collection);
    }
}
