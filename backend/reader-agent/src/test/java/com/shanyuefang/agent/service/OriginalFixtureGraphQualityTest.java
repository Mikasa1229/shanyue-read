package com.shanyuefang.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Deterministic quality gate for the original, copyright-cleared graph fixture. */
class OriginalFixtureGraphQualityTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fixtureCoversTenChaptersAndKeepsSameNameIdentitiesSeparate() throws Exception {
        JsonNode fixture = objectMapper.readTree(resource());
        assertThat(fixture.path("license").asText()).isEqualTo("original-synthetic");
        JsonNode chapters = fixture.path("chapters");
        assertThat(chapters).hasSize(10);

        List<StructuredGraphExtractor.Entity> entities = new ArrayList<>();
        for (JsonNode chapter : chapters) {
            String text = chapter.path("content").asText();
            if (text.contains("城东的黎青")) {
                entities.add(new StructuredGraphExtractor.Entity("黎青", "CHARACTER", "城东的黎青", List.of("黎青"), "城东的黎青", 0.9D));
            }
            if (text.contains("城西的黎青")) {
                entities.add(new StructuredGraphExtractor.Entity("黎青", "CHARACTER", "城西的黎青", List.of("黎青"), "城西的黎青", 0.9D));
            }
        }
        Set<String> identityKeys = entities.stream().map(entity -> entity.type() + ":" + entity.name() + ":" + entity.identityHint()).collect(java.util.stream.Collectors.toSet());
        assertThat(identityKeys).containsExactlyInAnyOrder("CHARACTER:黎青:城东的黎青", "CHARACTER:黎青:城西的黎青");

        String first = chapters.get(0).path("content").asText();
        StructuredGraphExtractor.Extraction rejected = StructuredGraphExtractor.validateEvidence(
                new StructuredGraphExtractor.Extraction(List.of(new StructuredGraphExtractor.Entity(
                        "黎青", "CHARACTER", "港外的黎青", List.of(), "城东的黎青", 0.9D)), List.of()), first);
        assertThat(rejected.entities()).isEmpty();
    }

    @Test
    void fixtureContainsEvidenceBackedClueLifecycleAndCausalChain() throws Exception {
        JsonNode chapters = objectMapper.readTree(resource()).path("chapters");
        String all = chapters.toString();
        assertThat(all).contains("秘密", "线索", "密函", "冲突", "揭晓");
        assertThat(all.indexOf("铜铃")).isLessThan(all.indexOf("铜铃停止低鸣"));
        assertThat(all.indexOf("蓝色蜡印")).isLessThan(all.indexOf("发生冲突"));
        assertThat(all.indexOf("密函")).isLessThan(all.indexOf("谜团开始回收"));
    }

    private InputStream resource() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("agent-original-fixture.json");
        assertThat(stream).as("original fixture resource").isNotNull();
        return stream;
    }
}
