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
            assertFalse(root.path("retrievalCases").isEmpty());
            for (JsonNode evaluation : root.path("retrievalCases")) {
                assertFalse(evaluation.path("id").asText().isBlank());
                assertFalse(evaluation.path("query").asText().isBlank());
                assertEquals(99, evaluation.path("maxChapterIndex").asInt(),
                        "chapterIndex is zero-based; the first 100 chapters end at 99");
                assertTrue(evaluation.path("expectedTerms").isArray());
                assertFalse(evaluation.path("expectedTerms").isEmpty());
            }
        }
    }
}
