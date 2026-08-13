package com.shanyuefang.novel.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.shanyuefang.novel.domain.entity.BookContentVersion;
import com.shanyuefang.novel.domain.vo.SearchBookVO;
import com.shanyuefang.novel.domain.vo.AggregatedBookVO;
import com.shanyuefang.novel.mapper.BookContentVersionMapper;
import com.shanyuefang.novel.messaging.KnowledgeIndexPublisher;
import com.shanyuefang.novel.service.BookSourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookSourceControllerTest {
    @Mock private BookSourceService bookSourceService;
    @Mock private KnowledgeIndexPublisher publisher;
    @Mock private BookContentVersionMapper versionMapper;

    @Test
    void newContentPersistsSha256VersionBeforePublishing() {
        SearchBookVO detail = indexedBook();
        when(bookSourceService.getContent(7L, "chapter-url")).thenReturn("chapter content");
        when(bookSourceService.getBookDetail(7L, "book-url")).thenReturn(detail);
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        Map<String, String> response = controller().content(7L, "chapter-url", "book-url", 4).getData();

        assertThat(response).containsEntry("content", "chapter content");
        ArgumentCaptor<BookContentVersion> version = ArgumentCaptor.forClass(BookContentVersion.class);
        verify(versionMapper).insert(version.capture());
        assertThat(version.getValue().getCanonicalBookId()).isEqualTo(88L);
        assertThat(version.getValue().getChapterIndex()).isEqualTo(4);
        assertThat(version.getValue().getIndexStatus()).isEqualTo("PENDING");
        assertThat(version.getValue().getContentHash()).hasSize(64);
        verify(publisher).publish(eq(88L), eq(4), eq("chapter content"), eq(version.getValue().getContentHash()));
    }

    @Test
    void readyContentVersionIsNotPublishedAgain() {
        BookContentVersion ready = new BookContentVersion();
        ready.setIndexStatus("READY");
        when(bookSourceService.getContent(7L, "chapter-url")).thenReturn("chapter content");
        when(bookSourceService.getBookDetail(7L, "book-url")).thenReturn(indexedBook());
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(ready);

        controller().content(7L, "chapter-url", "book-url", 4);

        verify(versionMapper, never()).insert(any(BookContentVersion.class));
        verify(versionMapper, never()).updateById(any(BookContentVersion.class));
        verify(publisher, never()).publish(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyString(), anyString());
    }

    @Test
    void failedContentVersionReturnsToPendingAndIsRepublished() {
        BookContentVersion failed = new BookContentVersion();
        failed.setIndexStatus("FAILED");
        when(bookSourceService.getContent(7L, "chapter-url")).thenReturn("chapter content");
        when(bookSourceService.getBookDetail(7L, "book-url")).thenReturn(indexedBook());
        when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(failed);

        controller().content(7L, "chapter-url", "book-url", 4);

        assertThat(failed.getIndexStatus()).isEqualTo("PENDING");
        verify(versionMapper).updateById(failed);
        verify(publisher).publish(eq(88L), eq(4), eq("chapter content"), anyString());
    }

    @Test
    void canonicalSearchUsesTheNewMirrorPreservingServiceContract() {
        AggregatedBookVO work = new AggregatedBookVO();
        work.setCanonicalBookId(88L);
        work.setSourceCount(2);
        when(bookSourceService.aggregateCanonicalSearch(9L, "剑来", 1)).thenReturn(List.of(work));

        List<AggregatedBookVO> response = controller().aggregateCanonicalSearch(9L, "剑来", 1).getData();

        assertThat(response).singleElement().extracting(AggregatedBookVO::getSourceCount).isEqualTo(2);
        verify(bookSourceService).aggregateCanonicalSearch(9L, "剑来", 1);
    }

    private BookSourceController controller() {
        return new BookSourceController(bookSourceService, publisher, versionMapper);
    }

    private SearchBookVO indexedBook() {
        SearchBookVO detail = new SearchBookVO();
        detail.setCanonicalBookId(88L);
        return detail;
    }
}
