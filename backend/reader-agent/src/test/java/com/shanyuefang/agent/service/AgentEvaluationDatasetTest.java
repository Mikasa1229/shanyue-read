package com.shanyuefang.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEvaluationDatasetTest {
    @Test
    void evaluationDatasetCoversAllSafetyAndProductGates() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/agent-eval-cases.json")) {
            assertNotNull(input, "evaluation dataset must be packaged with Agent tests");
            List<Map<String, String>> cases = new ObjectMapper().readValue(input, new TypeReference<>() { });
            Set<String> categories = cases.stream().map(value -> value.get("category")).collect(java.util.stream.Collectors.toSet());
            assertTrue(cases.size() >= 8);
            assertEquals(Set.of("citation", "spoiler", "refusal", "graph", "clue", "recommendation", "tool-security"), categories);
            assertTrue(cases.stream().allMatch(value -> value.get("id") != null && value.get("expected") != null));
        }
    }
}
