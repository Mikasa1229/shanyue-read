package com.shanyuefang.novel.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.shanyuefang.novel.domain.entity.BookContentVersion;
import com.shanyuefang.novel.domain.entity.ContentRecoveryTask;
import com.shanyuefang.novel.mapper.BookContentVersionMapper;
import com.shanyuefang.novel.mapper.ContentRecoveryTaskMapper;
import com.shanyuefang.novel.messaging.ContentRecoveryPublisher;
import com.shanyuefang.novel.messaging.KnowledgeIndexPublisher;
import com.shanyuefang.novel.service.impl.ContentRecoveryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentRecoveryServiceImplTest {
    @Mock private ContentRecoveryTaskMapper tasks;
    @Mock private BookContentVersionMapper versions;
    @Mock private BookSourceService sources;
    @Mock private KnowledgeIndexPublisher indexes;
    @Mock private ContentRecoveryPublisher recoveries;

    @Test
    void enqueuePersistsABoundedTaskAndPublishesItsId() {
        when(versions.selectList(any(Wrapper.class))).thenReturn(List.of(version(4)));

        ContentRecoveryTask task = service().enqueue(9L, 4, 4);

        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getTotalChapters()).isEqualTo(1);
        ArgumentCaptor<ContentRecoveryTask> stored = ArgumentCaptor.forClass(ContentRecoveryTask.class);
        verify(tasks).insert(stored.capture());
        verify(recoveries).publish(stored.getValue().getId());
    }

    @Test
    void recoveryRepublishesSourceTextThroughTheExistingIndexQueue() {
        ContentRecoveryTask task = new ContentRecoveryTask();
        task.setId(5L); task.setCanonicalBookId(9L); task.setStartChapter(4); task.setEndChapter(4);
        when(tasks.claim(5L)).thenReturn(1);
        when(tasks.selectById(5L)).thenReturn(task);
        when(versions.selectList(any(Wrapper.class))).thenReturn(List.of(version(4)));
        when(sources.getContent(7L, "chapter-4")).thenReturn("可恢复的章节正文");

        service().recover(5L);

        verify(indexes).publish(eq(9L), eq(4), eq("可恢复的章节正文"), anyString());
        verify(versions).updateById(any(BookContentVersion.class));
        assertThat(task.getStatus()).isEqualTo("COMPLETED");
        assertThat(task.getCompletedChapters()).isEqualTo(1);
    }

    @Test
    void unchangedTaskIsNotExecutedTwice() {
        when(tasks.claim(5L)).thenReturn(0);

        service().recover(5L);

        verify(tasks, never()).selectById(anyLong());
        verify(indexes, never()).publish(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyString(), anyString());
    }

    private ContentRecoveryServiceImpl service() {
        return new ContentRecoveryServiceImpl(tasks, versions, sources, indexes, recoveries);
    }

    private BookContentVersion version(int chapter) {
        BookContentVersion value = new BookContentVersion();
        value.setCanonicalBookId(9L); value.setSourceId(7L); value.setChapterIndex(chapter); value.setChapterUrl("chapter-" + chapter);
        value.setContentHash("old-hash"); value.setFetchedAt(LocalDateTime.now()); value.setIndexStatus("READY");
        return value;
    }
}
