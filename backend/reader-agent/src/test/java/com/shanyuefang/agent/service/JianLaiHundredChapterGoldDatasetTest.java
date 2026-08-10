package com.shanyuefang.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the one-based display range and zero-based retrieval boundary of the 100-chapter gold. */
class JianLaiHundredChapterGoldDatasetTest {
    @Test
    void retrievalCasesCannotReadPastTheFirstHundredChapters() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/agent-jianlai-100-gold.json")) {
            assertNotNull(input, "100-chapter Jian Lai gold must be packaged with Agent tests");
            JsonNode root = new ObjectMapper().readTree(input);
            assertEquals(1, root.path("chapterRange").path("start").asInt());
            assertEquals(100, root.path("chapterRange").path("end").asInt());
            assertEquals("2.0", root.path("schemaVersion").asText());
            assertTrue(root.path("requiredRelations").size() >= 8,
                    "the relationship benchmark must cover more than family relations");
            for (JsonNode relation : root.path("requiredRelations")) {
                assertFalse(relation.path("id").asText().isBlank());
                assertFalse(relation.path("source").asText().isBlank());
                assertFalse(relation.path("target").asText().isBlank());
                assertTrue(relation.path("acceptedTypes").isArray());
                assertFalse(relation.path("acceptedTypes").isEmpty());
                assertTrue(relation.path("evidenceChapters").isArray());
                assertFalse(relation.path("evidenceChapters").isEmpty());
                assertTrue(relation.path("direction").asText().matches("FORWARD|EITHER"));
            }
            assertFalse(root.path("retrievalCases").isEmpty());
            assertTrue(root.path("retrievalCases").size() >= 9,
                    "retrieval evaluation needs relationship, alias, and spoiler coverage");
            for (JsonNode evaluation : root.path("retrievalCases")) {
                assertFalse(evaluation.path("id").asText().isBlank());
                assertFalse(evaluation.path("query").asText().isBlank());
                assertEquals(99, evaluation.path("maxChapterIndex").asInt(),
                        "chapterIndex is zero-based; the first 100 chapters end at 99");
                assertTrue(evaluation.path("expectedTerms").isArray());
                assertFalse(evaluation.path("expectedTerms").isEmpty());
                assertTrue(evaluation.path("expectedEvidenceChapters").isArray());
                assertFalse(evaluation.path("expectedEvidenceChapters").isEmpty());
            }
        }
    }
}
