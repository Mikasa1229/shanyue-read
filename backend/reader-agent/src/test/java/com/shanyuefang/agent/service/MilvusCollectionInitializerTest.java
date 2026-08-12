package com.shanyuefang.agent.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DescribeIndexResponse;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.index.DescribeIndexParam;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusCollectionInitializerTest {
    @Test
    void createsAndLoadsAFirstUseCollectionWhenDescribeIndexThrows() {
        MilvusServiceClient client = mock(MilvusServiceClient.class);
        when(client.hasCollection(any(HasCollectionParam.class))).thenReturn(R.success(false));
        when(client.createCollection(any(CreateCollectionParam.class))).thenReturn(R.success());
        when(client.describeIndex(any(DescribeIndexParam.class))).thenThrow(new RuntimeException("index not found"));
        when(client.createIndex(any(CreateIndexParam.class))).thenReturn(R.success());
        when(client.loadCollection(any(LoadCollectionParam.class))).thenReturn(R.success());

        assertDoesNotThrow(() -> new MilvusCollectionInitializer().ensure(client, "first_use_vectors", 256));

        verify(client).createCollection(any(CreateCollectionParam.class));
        verify(client).createIndex(any(CreateIndexParam.class));
        verify(client).loadCollection(any(LoadCollectionParam.class));
    }

    @Test
    void doesNotDuplicateAnExistingIndex() {
        MilvusServiceClient client = mock(MilvusServiceClient.class);
        when(client.hasCollection(any(HasCollectionParam.class))).thenReturn(R.success(true));
        when(client.describeIndex(any(DescribeIndexParam.class))).thenReturn(R.success(DescribeIndexResponse.getDefaultInstance()));
        when(client.loadCollection(any(LoadCollectionParam.class))).thenReturn(R.success());

        new MilvusCollectionInitializer().ensure(client, "existing_vectors", 256);

        verify(client, times(0)).createCollection(any(CreateCollectionParam.class));
        verify(client, times(0)).createIndex(any(CreateIndexParam.class));
        verify(client).loadCollection(any(LoadCollectionParam.class));
    }
}
