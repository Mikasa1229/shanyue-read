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
        int overallProgress = progressFor("EXTRACT", completed, totalChapters);
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, Wrappers.<BookKnowledgeBuildTask>lambdaUpdate()
                .eq(BookKnowledgeBuildTask::getId, taskId)
                .eq(BookKnowledgeBuildTask::getStatus, "RUNNING")
                .set(BookKnowledgeBuildTask::getCompletedChapters, completed)
                .set(BookKnowledgeBuildTask::getCurrentStage, "EXTRACT")
                .set(BookKnowledgeBuildTask::getStageCompletedUnits, completed)
                .set(BookKnowledgeBuildTask::getStageTotalUnits, totalChapters)
                .set(BookKnowledgeBuildTask::getOverallProgress, overallProgress)
                .set(BookKnowledgeBuildTask::getMessage, message)
                .set(BookKnowledgeBuildTask::getUpdatedAt, now));
        spaceMapper.update(null, Wrappers.<BookKnowledgeSpace>lambdaUpdate()
                .eq(BookKnowledgeSpace::getCanonicalBookId, canonicalBookId)
                .set(BookKnowledgeSpace::getStatus, "RUNNING")
                .set(BookKnowledgeSpace::getCompletedChapters, completed)
                .set(BookKnowledgeSpace::getFailureMessage, null)
                .set(BookKnowledgeSpace::getUpdatedAt, now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStage(long taskId, String stage, int completedUnits, int totalUnits, String message) {
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, Wrappers.<BookKnowledgeBuildTask>lambdaUpdate()
                .eq(BookKnowledgeBuildTask::getId, taskId)
                .eq(BookKnowledgeBuildTask::getStatus, "RUNNING")
                .set(BookKnowledgeBuildTask::getCurrentStage, stage)
                .set(BookKnowledgeBuildTask::getStageCompletedUnits, Math.max(0, completedUnits))
                .set(BookKnowledgeBuildTask::getStageTotalUnits, Math.max(1, totalUnits))
                .set(BookKnowledgeBuildTask::getOverallProgress, progressFor(stage, completedUnits, totalUnits))
                .set(BookKnowledgeBuildTask::getMessage, message)
                .set(BookKnowledgeBuildTask::getUpdatedAt, now));
    }

    private int progressFor(String stage, int completedUnits, int totalUnits) {
        int completed = Math.max(0, completedUnits);
        int total = Math.max(1, totalUnits);
        int ratio = Math.min(100, Math.round(completed * 100f / total));
        return switch (stage) {
            case "EXTRACT" -> Math.round(ratio * .70f);
            case "CHARACTER_CALIBRATION" -> 70 + Math.round(ratio * .08f);
            case "STORY_EVENTS" -> 78 + Math.round(ratio * .06f);
            case "CLUE_SYNTHESIS" -> 84 + Math.round(ratio * .05f);
            case "CLUE_LIFECYCLE" -> 89 + Math.round(ratio * .04f);
            case "RAG_REFRESH" -> 93 + Math.round(ratio * .04f);
            case "GRAPH_PROJECTION" -> 97 + Math.round(ratio * .02f);
            // Completion is the only point that may show 100%.
            case "FINALIZE" -> 99;
            default -> 0;
        };
    }
}
