package com.shanyuefang.agent.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/** Adapts the configured embedding provider (or its deterministic fallback) to Spring AI's VectorStore contract. */
@Component
public class HashEmbeddingClient implements EmbeddingClient {
    private final EmbeddingService embeddingService;

    public HashEmbeddingClient(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<List<Double>> vectors = embeddingService.embedAll(request.getInstructions());
        List<Embedding> embeddings = IntStream.range(0, vectors.size())
                .mapToObj(index -> new Embedding(vectors.get(index), index)).toList();
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public List<Double> embed(Document document) {
        return embeddingService.embed(document.getContent());
    }

    @Override
    public int dimensions() {
        return embeddingService.dimensions();
    }
}
