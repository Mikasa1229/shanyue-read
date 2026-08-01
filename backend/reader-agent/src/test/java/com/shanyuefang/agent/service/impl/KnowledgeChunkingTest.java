package com.shanyuefang.agent.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkingTest {
    @Test
    void preservesSentenceBoundariesAndCarriesBoundedOverlap() {
        String first = "甲在城门前等待，听见远处钟声。".repeat(35);
        String second = "乙随后抵达，两人约定次日同行。".repeat(35);

        List<String> chunks = KnowledgeServiceImpl.chunks(first + second);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(800));
        assertThat(chunks.get(0)).endsWith("。");
        assertThat(chunks.get(1)).contains(chunks.get(0).substring(chunks.get(0).length() - 120));
    }

    @Test
    void safelySplitsOneSentenceThatExceedsTheTarget() {
        List<String> chunks = KnowledgeServiceImpl.chunks("甲".repeat(1700));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(800));
    }
}
