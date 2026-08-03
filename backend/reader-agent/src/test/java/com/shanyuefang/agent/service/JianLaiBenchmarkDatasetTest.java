package com.shanyuefang.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the real indexed Jian Lai corpus benchmark without storing copyrighted text. */
class JianLaiBenchmarkDatasetTest {
    @Test
    void realNovelCasesAreChineseBoundaryAwareAndDoNotEmbedNovelText() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/agent-jianlai-benchmark-cases.json")) {
            assertNotNull(input, "Jian Lai benchmark corpus must be packaged with Agent tests");
            JsonNode root = new ObjectMapper().readTree(input);
            assertEquals(358679512818388992L, root.path("canonicalBookId").asLong());
            assertEquals("剑来", root.path("title").asText());
            JsonNode cases = root.path("cases");
            assertTrue(cases.isArray() && cases.size() >= 12);
            Set<String> ids = new HashSet<>();
            for (JsonNode value : cases) {
                assertTrue(ids.add(value.path("id").asText()), "case ids must be unique");
                assertFalse(value.path("prompt").asText().isBlank());
                assertFalse(value.path("review").asText().isBlank());
                assertTrue(value.path("boundary").asInt(-1) >= 0);
                assertTrue(value.path("mustHaveEvents").isArray());
                assertFalse(value.has("content"), "benchmark must not embed novel content");
            }
        }
    }
}
