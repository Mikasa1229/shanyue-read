package com.shanyuefang.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.agent.domain.entity.BookKnowledgeBuildTask;
import com.shanyuefang.agent.domain.entity.BookKnowledgeSpace;
import com.shanyuefang.agent.mapper.BookKnowledgeBuildTaskMapper;
import com.shanyuefang.agent.mapper.BookKnowledgeSpaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Makes completed chapters observable while the long graph-write transaction is still running. */
@Service
@RequiredArgsConstructor
public class BookKnowledgeBuildProgressService {
    private final BookKnowledgeBuildTaskMapper taskMapper;
    private final BookKnowledgeSpaceMapper spaceMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long taskId, long canonicalBookId, int totalChapters, int startChapter, int completed) {
        String message = completed >= totalChapters
                ? "已完成 " + completed + " / " + totalChapters + " 章的大模型关系抽取"
                : "已完成 " + completed + " / " + totalChapters + " 章，正在分析第 " + (startChapter + completed) + " 章";
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, Wrappers.<BookKnowledgeBuildTask>lambdaUpdate()
                .eq(BookKnowledgeBuildTask::getId, taskId)
                .eq(BookKnowledgeBuildTask::getStatus, "RUNNING")
                .set(BookKnowledgeBuildTask::getCompletedChapters, completed)
                .set(BookKnowledgeBuildTask::getMessage, message)
                .set(BookKnowledgeBuildTask::getUpdatedAt, now));
        spaceMapper.update(null, Wrappers.<BookKnowledgeSpace>lambdaUpdate()
                .eq(BookKnowledgeSpace::getCanonicalBookId, canonicalBookId)
                .set(BookKnowledgeSpace::getStatus, "RUNNING")
                .set(BookKnowledgeSpace::getCompletedChapters, completed)
                .set(BookKnowledgeSpace::getFailureMessage, null)
                .set(BookKnowledgeSpace::getUpdatedAt, now));
    }
}
