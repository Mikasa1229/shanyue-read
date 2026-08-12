package com.shanyuefang.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NovelContentNormalizerTest {
    @Test
    void removesMojibakeTailWithoutChangingCanonicalHash() {
        String clean = "陈平安走过石桥。山风吹来，远处传来钟声。";
        String polluted = clean + "\n锟斤拷锟斤拷锟斤拷\nwww.example.com";
        var left = NovelContentNormalizer.analyze(clean);
        var right = NovelContentNormalizer.analyze(polluted);
        assertThat(right.normalizedContent()).isEqualTo(left.normalizedContent());
        assertThat(right.normalizedHash()).isEqualTo(left.normalizedHash());
        assertThat(right.qualityScore()).isGreaterThan(0.5D);
    }

    @Test
    void keepsRealContentChangesVisible() {
        var left = NovelContentNormalizer.analyze("陈平安走过石桥。山风吹来。");
        var right = NovelContentNormalizer.analyze("陈平安走过石桥。天空突然下起大雪。");
        assertThat(right.normalizedHash()).isNotEqualTo(left.normalizedHash());
        assertThat(NovelContentNormalizer.similarity(left.semanticFingerprint(), right.semanticFingerprint())).isLessThan(0.99D);
    }
}
