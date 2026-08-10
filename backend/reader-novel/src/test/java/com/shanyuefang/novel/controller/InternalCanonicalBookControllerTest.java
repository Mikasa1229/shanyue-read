package com.shanyuefang.novel.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalCanonicalBookControllerTest {
    @Test
    void extractsExplicitBookExclusionsWithoutTreatingShelfAsABook() {
        assertEquals("剑来", InternalCanonicalBookController.exclusionTerms("推荐一本，但不要《剑来》相关作品").get(0));
        assertTrue(InternalCanonicalBookController.exclusionTerms("不要从书架里找").isEmpty());
    }

    @Test
    void extractsAUsefulSourceDiscoveryKeywordFromARecommendationCall() {
        assertEquals("悬疑", InternalCanonicalBookController.discoveryKeyword("请从书源搜索一本悬疑小说"));
    }
}
