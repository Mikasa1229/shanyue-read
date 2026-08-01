package com.shanyuefang.agent.service.impl;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.service.EmbeddingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A deterministic development embedding. It keeps RAG usable without consuming a model API;
 * ConfiguredEmbeddingService can delegate to a real provider while retaining this fallback.
 */
@Service("hashEmbeddingService")
public class HashEmbeddingService implements EmbeddingService {
    private final int dimension;

    public HashEmbeddingService(AgentProperties properties) {
        this.dimension = Math.max(1, Math.min(properties.getEmbeddingDimensions(), 4096));
    }

    @Override
    public List<Double> embed(String text) {
        double[] vector = new double[dimension];
        String value = text == null ? "" : text.replaceAll("\\s+", "");
        for (int index = 0; index < value.length(); index++) {
            int codePoint = value.codePointAt(index);
            vector[Math.floorMod(codePoint * 31 + index * 17, dimension)] += 1.0d;
            if (index + 1 < value.length()) {
                int bigram = codePoint * 131 + value.codePointAt(index + 1);
                vector[Math.floorMod(bigram, dimension)] += 0.5d;
            }
        }
        double norm = 0d;
        for (double item : vector) norm += item * item;
        norm = Math.sqrt(norm);
        List<Double> result = new ArrayList<>(dimension);
        for (double item : vector) result.add(norm == 0d ? 0d : item / norm);
        return result;
    }

    @Override
    public int dimensions() {
        return dimension;
    }

    @Override
    public double similarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.size() != right.size()) return 0d;
        double score = 0d;
        for (int index = 0; index < left.size(); index++) score += left.get(index) * right.get(index);
        return score;
    }
}
