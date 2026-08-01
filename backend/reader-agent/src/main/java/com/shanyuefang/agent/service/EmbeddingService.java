package com.shanyuefang.agent.service;

import java.util.List;

/** Embedding boundary: production can replace this with a Milvus-compatible embedding provider. */
public interface EmbeddingService {
    List<Double> embed(String text);

    /** Batch boundary lets a provider send one request for a chapter's chunk set. */
    default List<List<Double>> embedAll(List<String> texts) {
        return texts == null ? List.of() : texts.stream().map(this::embed).toList();
    }

    /** Every active vector collection is dimensioned from this value. */
    default int dimensions() {
        return 256;
    }

    double similarity(List<Double> left, List<Double> right);
}
