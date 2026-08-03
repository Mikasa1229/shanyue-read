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

/** Schema, boundary, and copyright-safety checks for the public Jian Lai knowledge gold. */
class JianLaiKnowledgeGoldDatasetTest {
    private static final long JIAN_LAI_ID = 358679512818388992L;
    private static final Set<String> LEVELS = Set.of("PUBLIC_SAFE", "PUBLIC_PLOT", "FULL_SPOILER", "UNVERIFIED");

    @Test
    void publicGoldIsStableAndContainsNoNovelText() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/agent-jianlai-knowledge-gold.json")) {
            assertNotNull(input, "Jian Lai knowledge gold must be packaged with Agent tests");
            JsonNode root = new ObjectMapper().readTree(input);
            assertEquals("1.0", root.path("schemaVersion").asText());
            assertEquals(JIAN_LAI_ID, root.path("canonicalBookId").asLong());
            assertEquals("剑来", root.path("title").asText());
            assertTrue(root.path("sourceDocument").asText().endsWith("剑来公开知识基准.md"));

            JsonNode facts = root.path("facts");
            assertTrue(facts.isArray() && facts.size() >= 30);
            Set<String> factIds = new HashSet<>();
            for (JsonNode fact : facts) {
                assertTrue(factIds.add(fact.path("factId").asText()), "fact ids must be unique");
                assertFalse(fact.path("subject").asText().isBlank());
                assertFalse(fact.path("predicate").asText().isBlank());
                assertFalse(fact.path("object").asText().isBlank());
                assertTrue(LEVELS.contains(fact.path("level").asText()), "unknown spoiler level");
                assertFalse(fact.has("content"), "knowledge gold must not embed novel content");
            }

            JsonNode cases = root.path("evaluationCases");
            assertTrue(cases.isArray() && cases.size() >= 6);
            Set<String> caseIds = new HashSet<>();
            for (JsonNode evaluation : cases) {
                assertTrue(caseIds.add(evaluation.path("id").asText()), "case ids must be unique");
                assertFalse(evaluation.path("prompt").asText().isBlank());
                String maxSpoiler = evaluation.path("maxSpoiler").asText();
                assertTrue(LEVELS.contains(maxSpoiler));
                JsonNode expectedFacts = evaluation.path("expectedFacts");
                assertTrue(expectedFacts.isArray() && expectedFacts.size() > 0);
                for (JsonNode factId : expectedFacts) {
                    assertTrue(factIds.contains(factId.asText()), "case references an unknown fact");
                }
                if (evaluation.path("mustRefuseFutureFacts").asBoolean(false)) {
                    assertFalse("FULL_SPOILER".equals(maxSpoiler), "future-fact refusal must have a reader-safe boundary");
                }
                assertFalse(evaluation.has("content"), "evaluation must not embed novel content");
            }
        }
    }
}
