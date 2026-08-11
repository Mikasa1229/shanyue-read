package com.shanyuefang.novel.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BookSourceSearchRelevanceTest {
    @Test
    void exactTitleOutranksDerivativeTitlesRegardlessOfTheirSourceCount() {
        int exactMatch = BookSourceServiceImpl.searchRelevance("剑来", "《剑来》", "烽火戏诸侯");
        int prefixedMatch = BookSourceServiceImpl.searchRelevance("剑来", "剑来大纲", "一念化沧海");
        int containedMatch = BookSourceServiceImpl.searchRelevance("剑来", "万界最强剑来系统", "哑舍");

        assertTrue(exactMatch < prefixedMatch);
        assertTrue(prefixedMatch < containedMatch);
    }

    @Test
    void titleMatchesOutrankAuthorOnlyMatches() {
        int titleMatch = BookSourceServiceImpl.searchRelevance("剑来", "从剑来被认为是武神开始", "热炒蛤蜊挺有味");
        int authorMatch = BookSourceServiceImpl.searchRelevance("剑来", "仙来！", "剑来");

        assertTrue(titleMatch < authorMatch);
    }

    @Test
    void authorPrefixesShareOneAggregateIdentity() {
        org.junit.jupiter.api.Assertions.assertEquals("烽火戏诸侯", BookSourceServiceImpl.normalizedAuthorField("作者：烽火戏诸侯"));
        org.junit.jupiter.api.Assertions.assertEquals(BookSourceServiceImpl.normalizedAuthorField("烽火戏诸侯"),
                BookSourceServiceImpl.normalizedAuthorField("作者：烽火戏诸侯"));
    }
}
