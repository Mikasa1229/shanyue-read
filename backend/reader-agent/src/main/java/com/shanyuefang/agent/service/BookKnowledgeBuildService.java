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
    List<BookKnowledgeBuildTask> myTasks(long userId, int limit);
    void deleteTask(long userId, long taskId);
    Map<Long, Map<String, Object>> statuses(Collection<Long> canonicalBookIds);
    Map<String, Object> status(long canonicalBookId);
    void markCleared(long canonicalBookId);
}
