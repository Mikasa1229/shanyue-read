package com.shanyuefang.agent.service;

import com.shanyuefang.agent.domain.dto.StartBookKnowledgeBuildDTO;
import com.shanyuefang.agent.domain.entity.BookKnowledgeBuildTask;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface BookKnowledgeBuildService {
    Map<String, Object> prepare(long userId, long canonicalBookId);
    default Map<String, Object> prepare(long userId, long canonicalBookId, Integer startChapter, Integer endChapter) {
        return prepare(userId, canonicalBookId);
    }
    BookKnowledgeBuildTask start(long userId, long canonicalBookId, StartBookKnowledgeBuildDTO dto);
    /** Consumes one durable graph-build message. Returns false for an already terminal or claimed task. */
    boolean consumeQueuedTask(long taskId);
    List<BookKnowledgeBuildTask> myTasks(long userId, int limit);
    void deleteTask(long userId, long taskId);
    void updateSharing(long userId, long canonicalBookId, boolean isPublic);
    void deleteOwnedGraph(long userId, long canonicalBookId);
    void ensureReadable(long userId, long canonicalBookId);
    Map<Long, Map<String, Object>> statuses(Collection<Long> canonicalBookIds);
    Map<String, Object> status(long canonicalBookId);
    void markCleared(long canonicalBookId);
    void synchronizeCompletedRange(long canonicalBookId, int startChapter, int endChapter, boolean replaceExisting);
    void synchronizeAllIndexedChapters(long canonicalBookId, boolean replaceExisting);
}
