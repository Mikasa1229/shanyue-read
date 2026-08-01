package com.shanyuefang.agent.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructuredGraphExtractorTest {
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
}
