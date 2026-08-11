package com.shanyuefang.agent.service.impl;

import com.shanyuefang.agent.mapper.UserModelConfigMapper;
import com.shanyuefang.agent.domain.entity.AgentMessage;
import com.shanyuefang.agent.domain.vo.BookReferenceVO;
import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

class AgentServiceImplTest {
    @Test
    void acceptsPublicOpenAiCompatibleEndpointsAndPreservesLegacyDefaults() {
        assertEquals("https://api.deepseek.com", AgentServiceImpl.normalizeBaseUrl(null, "deepseek"));
        assertEquals("https://api.openai.com", AgentServiceImpl.normalizeBaseUrl("", "openai"));
        assertEquals("https://api.deepseek.com", AgentServiceImpl.normalizeBaseUrl("https://api.deepseek.com/v1/", "openai"));
    }

    @Test
    void rejectsUntrustedOrUnsafeByokEndpoints() {
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("file:///local-model", "openai"));
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("http://api.openai.com", "openai"));
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("https://127.0.0.1/v1", "openai"));
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("https://127.0.0.1/v1", "openai", "127.0.0.1"));
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("https://example.test/v1", "openai"));
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("https://api.openai.com/?target=internal", "openai"));
    }

    @Test
    void removesOptionalV1SuffixBeforeSpringAiBuildsChatCompletionsPath() {
        assertEquals("https://api.xiaomimimo.com", AgentServiceImpl.chatCompletionsBaseUrl("https://api.xiaomimimo.com/v1"));
        assertEquals("https://api.deepseek.com", AgentServiceImpl.chatCompletionsBaseUrl("https://api.deepseek.com"));
    }

    @Test
    void deletesOnlyTheOwnedActiveModelConfiguration() {
        UserModelConfigMapper mapper = mock(UserModelConfigMapper.class);
        when(mapper.deleteOwnedConfig(7L, 11L)).thenReturn(1);

        serviceWith(mapper).deleteModelConfig(7L, 11L);

        verify(mapper).deleteOwnedConfig(7L, 11L);
    }

    @Test
    void rejectsDeleteWhenNoOwnedActiveConfigurationWasRemoved() {
        UserModelConfigMapper mapper = mock(UserModelConfigMapper.class);
        when(mapper.deleteOwnedConfig(7L, 11L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> serviceWith(mapper).deleteModelConfig(7L, 11L));
    }

    @Test
    void removesOnlyTheNewestCopyOfTheCurrentUserMessage() {
        List<AgentMessage> history = new ArrayList<>(List.of(
                message("USER", "推荐一本短篇"), message("ASSISTANT", "旧回答"), message("USER", "推荐一本短篇")));

        AgentServiceImpl.removeCurrentUserMessage(history, "推荐一本短篇");

        assertEquals(2, history.size());
        assertEquals("推荐一本短篇", history.get(0).getContent());
        assertEquals("旧回答", history.get(1).getContent());
    }

    @Test
    void turnsLatestNegativePreferenceIntoAnExplicitHardConstraint() {
        String summary = AgentServiceImpl.currentConstraintSummary("不要恋爱的小说，推荐一点有名的网文");

        assertTrue(summary.contains("硬性排除条件"));
        assertTrue(summary.contains("恋爱"));
        assertTrue(summary.contains("覆盖历史"));
    }

    @Test
    void exposesOnlyCatalogCandidatesActuallyMentionedInTheAnswer() {
        List<BookReferenceVO> candidates = List.of(
                new BookReferenceVO(1L, "剑来", "烽火戏诸侯", "", 2L, "book-url", ""),
                new BookReferenceVO(3L, "雪中悍刀行", "烽火戏诸侯", "", 2L, "other-url", ""));

        List<BookReferenceVO> references = AgentServiceImpl.referencedBooks("我推荐《剑来》，平台已有可用书源。", candidates);

        assertEquals(1, references.size());
        assertEquals("剑来", references.get(0).getTitle());
    }

    @Test
    void preservesVerifiedReferencesWhenModelDoesNotRepeatExactTitle() {
        List<BookReferenceVO> candidates = List.of(
                new BookReferenceVO(1L, "诡秘之主", "爱潜水的乌贼", "", 2L, "book-url", ""));

        List<BookReferenceVO> references = AgentServiceImpl.referencedBooks("这一本可以直接打开阅读。", candidates);

        assertEquals(1, references.size());
        assertEquals("诡秘之主", references.get(0).getTitle());
    }

    @Test
    void carriesRecentRecommendationCorrectionsIntoTheNextSearch() {
        String request = AgentServiceImpl.recommendationSearchRequest("我说了让你推荐一本", List.of(
                "推荐一本适合今晚读的书", "不要剑来", "我的意思是不要从书架里找", "你不会从书源里找吗"));

        assertTrue(request.contains("不要剑来"));
        assertTrue(request.contains("不要从书架里找"));
        assertTrue(request.contains("书源"));
    }

    @Test
    void mergesFunctionCallReferencesWithServerSideFallbackReferences() {
        BookReferenceVO first = new BookReferenceVO(1L, "剑来", "烽火戏诸侯", "", 2L, "book-url", "");
        BookReferenceVO second = new BookReferenceVO(3L, "诡秘之主", "爱潜水的乌贼", "", 4L, "other-url", "");

        List<BookReferenceVO> references = AgentServiceImpl.mergeBookReferences(List.of(first), List.of(first, second));

        assertEquals(2, references.size());
        assertEquals("诡秘之主", references.get(1).getTitle());
    }

    @Test
    void doesNotPrefetchAnOpenRecommendationBeforeNativeFunctionCalling() {
        assertTrue(!AgentServiceImpl.shouldPrefetchBookSearch("推荐一本适合今晚读的书", true));
        assertTrue(AgentServiceImpl.shouldPrefetchBookSearch("推荐一本适合今晚读的书", false));
        assertTrue(AgentServiceImpl.shouldPrefetchBookSearch("搜索《诡秘之主》", true));
        assertTrue(AgentServiceImpl.shouldPrefetchBookSearch("分析当前人物关系", true));
    }

    @Test
    void removesExplicitlyExcludedBooksFromClickableReferences() {
        List<BookReferenceVO> references = AgentServiceImpl.filterExcludedReferences("推荐一本，但不要《剑来》，不要从书架里找", List.of(
                new BookReferenceVO(1L, "剑来", "烽火戏诸侯", "", 2L, "book-url", ""),
                new BookReferenceVO(3L, "诡秘之主", "爱潜水的乌贼", "", 4L, "other-url", "")));

        assertEquals(1, references.size());
        assertEquals("诡秘之主", references.get(0).getTitle());
    }

    private AgentMessage message(String role, String content) {
        AgentMessage message = new AgentMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private AgentServiceImpl serviceWith(UserModelConfigMapper mapper) {
        return new AgentServiceImpl(null, null, mapper, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
