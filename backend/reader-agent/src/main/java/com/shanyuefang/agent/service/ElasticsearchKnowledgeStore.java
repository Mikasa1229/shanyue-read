package com.shanyuefang.agent.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.json.JsonData;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.entity.KnowledgeChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Keyword evidence recall within a LightRAG-selected work and spoiler boundary. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchKnowledgeStore {
    private final ObjectProvider<ElasticsearchClient> clientProvider;
    private final AgentProperties properties;

    public void index(KnowledgeChunk chunk) {
        ElasticsearchClient client = client(); if (client == null) return;
        try {
            ensureIndex(client);
            client.index(request -> request.index(properties.getElasticsearchIndex()).id(String.valueOf(chunk.getId())).document(Map.of(
                    "canonicalBookId", chunk.getCanonicalBookId(), "chapterIndex", chunk.getChapterIndex(), "chunkId", chunk.getId(),
                    "content", chunk.getContent(), "keywords", chunk.getKeywords() == null ? "" : chunk.getKeywords())));
        } catch (Exception exception) { log.warn("LightRAG keyword projection failed: chunkId={}", chunk.getId(), exception); }
    }

    public void removeChapter(long canonicalBookId, int chapterIndex) {
        ElasticsearchClient client = client(); if (client == null) return;
        try {
            if (!client.indices().exists(request -> request.index(properties.getElasticsearchIndex())).value()) return;
            client.deleteByQuery(request -> request.index(properties.getElasticsearchIndex()).query(query -> query.bool(bool -> bool
                    .filter(filter -> filter.term(term -> term.field("canonicalBookId").value(FieldValue.of(canonicalBookId))))
                    .filter(filter -> filter.term(term -> term.field("chapterIndex").value(FieldValue.of(chapterIndex)))))));
        } catch (Exception exception) { log.warn("LightRAG keyword chapter cleanup failed: bookId={}, chapter={}", canonicalBookId, chapterIndex, exception); }
    }

    public void removeBook(long canonicalBookId) {
        ElasticsearchClient client = client(); if (client == null) return;
        try {
            if (!client.indices().exists(request -> request.index(properties.getElasticsearchIndex())).value()) return;
            client.deleteByQuery(request -> request.index(properties.getElasticsearchIndex()).query(query -> query.term(term ->
                    term.field("canonicalBookId").value(FieldValue.of(canonicalBookId)))));
        } catch (Exception exception) { log.warn("LightRAG keyword book cleanup failed: bookId={}", canonicalBookId, exception); }
    }

    public List<Hit> search(long canonicalBookId, int currentChapter, String question, int limit) {
        ElasticsearchClient client = client(); if (client == null) return List.of();
        try {
            if (!client.indices().exists(request -> request.index(properties.getElasticsearchIndex())).value()) return List.of();
            return client.search(request -> request.index(properties.getElasticsearchIndex()).size(Math.max(8, limit * 3))
                    .query(query -> query.bool(bool -> bool
                            .filter(filter -> filter.term(term -> term.field("canonicalBookId").value(FieldValue.of(canonicalBookId))))
                            .filter(filter -> filter.range(range -> range.field("chapterIndex").lte(JsonData.of(currentChapter))))
                            .must(must -> must.multiMatch(match -> match.query(question).fields("content^2", "keywords"))))), Map.class)
                    .hits().hits().stream().map(hit -> toHit(hit.source())).filter(value -> value != null).toList();
        } catch (Exception exception) { log.warn("LightRAG keyword evidence recall failed", exception); return List.of(); }
    }

    private ElasticsearchClient client() {
        if ("false".equalsIgnoreCase(System.getenv("AGENT_ELASTICSEARCH_ENABLED"))) return null;
        return properties.isElasticsearchEnabled() ? clientProvider.getIfAvailable() : null;
    }
    private void ensureIndex(ElasticsearchClient client) throws IOException {
        if (!client.indices().exists(request -> request.index(properties.getElasticsearchIndex())).value()) {
            client.indices().create(request -> request.index(properties.getElasticsearchIndex()).settings(settings -> settings.numberOfReplicas("0")));
        }
    }
    private Hit toHit(Map<?, ?> source) {
        if (source == null || source.get("chapterIndex") == null || source.get("content") == null) return null;
        try { return new Hit(Integer.parseInt(String.valueOf(source.get("chapterIndex"))), String.valueOf(source.get("content"))); }
        catch (NumberFormatException ignored) { return null; }
    }
    public record Hit(int chapterIndex, String content) { }
}
