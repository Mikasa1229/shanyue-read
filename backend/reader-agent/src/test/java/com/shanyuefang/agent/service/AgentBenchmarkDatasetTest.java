package com.shanyuefang.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Schema and safety invariants for the reproducible benchmark corpus. */
class AgentBenchmarkDatasetTest {
    @Test
    void benchmarkHasEnoughCasesAndAllQualityDimensions() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/agent-benchmark-cases.json")) {
            assertNotNull(input, "benchmark corpus must be packaged with Agent tests");
            JsonNode root = new ObjectMapper().readTree(input);
            assertTrue(root.path("schemaVersion").asText().startsWith("1."));
            JsonNode cases = root.path("cases");
            assertTrue(cases.isArray() && cases.size() >= 20);
            Set<String> categories = new HashSet<>();
            Set<String> ids = new HashSet<>();
            for (JsonNode value : cases) {
                assertTrue(ids.add(value.path("id").asText()), "case ids must be unique");
                assertFalse(value.path("category").asText().isBlank());
                assertFalse(value.path("prompt").asText().isBlank());
                assertTrue(value.path("boundary").asInt(-1) >= 0);
                assertTrue(value.path("goldCitations").isArray());
                assertTrue(value.path("forbiddenCitations").isArray());
                assertFalse(value.path("contract").asText().isBlank());
                categories.add(value.path("category").asText());
                for (JsonNode chapter : value.path("goldCitations")) {
                    assertTrue(chapter.asInt() <= value.path("boundary").asInt(), "gold citation exceeds reading boundary");
                }
            }
            assertTrue(categories.containsAll(Set.of("fact", "citation", "spoiler", "graph", "clue",
                    "recommendation", "tool-security", "refusal", "character-interview", "reading-map", "cost", "resilience")));
        }
    }
}
