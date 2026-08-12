package com.shanyuefang.agent.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.AgentProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredGraphExtractorTest {
    @Test
    void graphModelClientDisablesNestedProviderRetries() throws Exception {
        StructuredGraphExtractor extractor = new StructuredGraphExtractor(new AgentProperties(), new ObjectMapper());
        java.lang.reflect.Method method = StructuredGraphExtractor.class.getDeclaredMethod("chatClient", StructuredGraphExtractor.ModelConfig.class,
                org.springframework.ai.openai.OpenAiChatOptions.class);
        method.setAccessible(true);
        org.springframework.ai.openai.OpenAiChatClient client = (org.springframework.ai.openai.OpenAiChatClient) method.invoke(extractor,
                new StructuredGraphExtractor.ModelConfig("test", "test-model", "https://example.invalid", "test-key"),
                new org.springframework.ai.openai.OpenAiChatOptions());

        AtomicInteger attempts = new AtomicInteger();
        try {
            client.retryTemplate.execute(context -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("simulated provider failure");
            });
        } catch (IllegalStateException ignored) {
            // Expected: the configured retry template must propagate the first provider failure.
        }
        assertEquals(1, attempts.get());
    }

    @Test
    void keepsOnlyModelFactsWithVerbatimChapterEvidence() {
        String chapter = "年轻的林默在雨夜与守卫周青相遇，林默将铜钥匙交给周青。";
        StructuredGraphExtractor.Extraction output = StructuredGraphExtractor.validateEvidence(
                new StructuredGraphExtractor.Extraction(
                        List.of(
                                new StructuredGraphExtractor.Entity("林默", "CHARACTER", "年轻的林默", List.of("林默", "不存在的别名"), "年轻的林默在雨夜与守卫周青相遇", 0.92D),
                                new StructuredGraphExtractor.Entity("杜撰角色", "CHARACTER", "", List.of(), "杜撰角色出现", 0.95D)),
                        List.of(
                                new StructuredGraphExtractor.Relation("林默", "年轻的林默", "周青", "", "KNOWS", "年轻的林默在雨夜与守卫周青相遇", 0.90D),
                                new StructuredGraphExtractor.Relation("林默", "", "杜撰角色", "", "KNOWS", "林默认识杜撰角色", 0.90D))),
                chapter);

        assertEquals(1, output.entities().size());
        assertEquals(List.of("林默"), output.entities().get(0).aliases());
        assertEquals(1, output.relations().size());
        assertEquals("周青", output.relations().get(0).target());
    }

    @Test
    void dropsSameNameHintThatCannotBeProvedByTheChapter() {
        String chapter = "城西的李青向林默传递消息。";
        StructuredGraphExtractor.Extraction output = StructuredGraphExtractor.validateEvidence(
                new StructuredGraphExtractor.Extraction(List.of(
                        new StructuredGraphExtractor.Entity("李青", "CHARACTER", "城东的李青", List.of(), "城西的李青向林默传递消息", 0.8D)), List.of()), chapter);

        assertEquals(0, output.entities().size());
    }

    @Test
    void acceptsAProviderAliasReturnedAsOneString() throws Exception {
        java.lang.reflect.Method method = StructuredGraphExtractor.class.getDeclaredMethod("normalizeAliases", Object.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> aliases = (List<String>) method.invoke(new StructuredGraphExtractor(null, null), "少年");

        assertEquals(List.of("少年"), aliases);
    }

    @Test
    void acceptsCanonicalRelationEndpointWhenEvidenceUsesVerifiedAlias() {
        String chapter = "黑衣少女宁静地看着陈平安，随后与陈平安同行。";
        StructuredGraphExtractor.Extraction output = StructuredGraphExtractor.validateEvidence(
                new StructuredGraphExtractor.Extraction(List.of(
                        new StructuredGraphExtractor.Entity("宁姚", "CHARACTER", "", List.of("黑衣少女"), "黑衣少女宁静地看着陈平安", 0.9D),
                        new StructuredGraphExtractor.Entity("陈平安", "CHARACTER", "", List.of(), "黑衣少女宁静地看着陈平安", 0.9D)),
                        List.of(new StructuredGraphExtractor.Relation("宁姚", "", "陈平安", "", "TRAVELS_WITH",
                                "黑衣少女宁静地看着陈平安，随后与陈平安同行", 0.9D))), chapter);

        assertEquals(2, output.entities().size());
        assertEquals(1, output.relations().size());
    }

    @Test
    void normalizesOnlyKnownProviderNodeTypeAliases() throws Exception {
        java.lang.reflect.Method method = StructuredGraphExtractor.class.getDeclaredMethod("normalizeNodeType", String.class);
        method.setAccessible(true);
        StructuredGraphExtractor extractor = new StructuredGraphExtractor(null, null);

        assertEquals("CHARACTER", method.invoke(extractor, "PERSON"));
        assertEquals("LOCATION", method.invoke(extractor, "place"));
        assertEquals(null, method.invoke(extractor, "OBJECT"));
    }

    @Test
    void acceptsSummarizedAtomicEventNameWithVerbatimEvidence() {
        String chapter = "陈平安拒绝收下锦衣少年给出的袋子，两人因此发生争执。";
        StructuredGraphExtractor.Extraction output = StructuredGraphExtractor.validateEvidence(
                new StructuredGraphExtractor.Extraction(List.of(new StructuredGraphExtractor.Entity(
                        "陈平安拒绝酬谢并引发争执", "EVENT", "", List.of(),
                        "陈平安拒绝收下锦衣少年给出的袋子，两人因此发生争执", 0.88D)), List.of()), chapter);

        assertEquals(1, output.entities().size());
    }

    @Test
    void characterKnowledgeCarriesEvidenceForIdentityAndSpecificRelations() {
        StructuredGraphExtractor.ChapterFact reveal = new StructuredGraphExtractor.ChapterFact(1L, 15,
                "黑衣少女表明自己是宁姚，随后宁姚教陈平安辨认药方。");
        StructuredGraphExtractor.CharacterKnowledgeExtraction extraction =
                new StructuredGraphExtractor.CharacterKnowledgeExtraction(
                        List.of(new StructuredGraphExtractor.IdentityResolution(
                                "宁姚", "黑衣少女", List.of(reveal), 0.96D)),
                        List.of(new StructuredGraphExtractor.CharacterRelation(
                                "宁姚", "陈平安", "TEACHER_OF", List.of(reveal), 0.91D)));

        assertEquals("宁姚", extraction.identities().get(0).canonicalName());
        assertEquals("黑衣少女", extraction.identities().get(0).mention());
        assertEquals("TEACHER_OF", extraction.relations().get(0).type());
        assertEquals(15, extraction.relations().get(0).evidence().get(0).chapterIndex());
    }

    @Test
    void onlyAcceptsExplicitAndLocallyBoundKnowledgeRelations() throws Exception {
        StructuredGraphExtractor extractor = new StructuredGraphExtractor(null, null);
        java.lang.reflect.Method method = StructuredGraphExtractor.class.getDeclaredMethod(
                "explicitKnowledgeRelation", String.class, String.class, String.class);
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(extractor, "林默认识周青，两人是多年的朋友。", "林默", "周青"));
        assertTrue((boolean) method.invoke(extractor, "林默与周青是隔壁邻居。", "林默", "周青"));
        assertFalse((boolean) method.invoke(extractor, "林默知道周青去了城外。", "林默", "周青"));
        assertFalse((boolean) method.invoke(extractor, "林默看见周青后，没有停下脚步。", "林默", "周青"));
        assertFalse((boolean) method.invoke(extractor, "林默对周青的评价令旁人意外。", "林默", "周青"));
    }
}
