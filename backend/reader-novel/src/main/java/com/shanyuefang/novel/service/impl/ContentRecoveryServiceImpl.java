package com.shanyuefang.novel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.NovelContentNormalizer;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.novel.domain.entity.BookContentVersion;
import com.shanyuefang.novel.domain.entity.ContentRecoveryTask;
import com.shanyuefang.novel.mapper.BookContentVersionMapper;
import com.shanyuefang.novel.mapper.ContentRecoveryTaskMapper;
import com.shanyuefang.novel.messaging.ContentRecoveryPublisher;
import com.shanyuefang.novel.messaging.KnowledgeIndexPublisher;
import com.shanyuefang.novel.service.BookSourceService;
import com.shanyuefang.novel.service.ContentRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Restores Agent evidence from the source-side audit ledger without pretending the text still exists locally. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentRecoveryServiceImpl implements ContentRecoveryService {
    private static final int MAX_CHAPTERS_PER_TASK = 200;

    private final ContentRecoveryTaskMapper taskMapper;
    private final BookContentVersionMapper versionMapper;
    private final BookSourceService bookSourceService;
    private final KnowledgeIndexPublisher knowledgeIndexPublisher;
    private final ContentRecoveryPublisher recoveryPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentRecoveryTask enqueue(long canonicalBookId, int startChapter, int endChapter) {
        if (canonicalBookId <= 0 || startChapter < 0 || endChapter < startChapter
                || endChapter - startChapter + 1 > MAX_CHAPTERS_PER_TASK) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Invalid chapter recovery range");
        }
        List<BookContentVersion> versions = versions(canonicalBookId, startChapter, endChapter);
        if (versions.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "No source chapter ledger exists in the requested range");
        }
        ContentRecoveryTask task = new ContentRecoveryTask();
        task.setId(SnowflakeIdUtil.next());
        task.setCanonicalBookId(canonicalBookId);
        task.setStartChapter(startChapter);
        task.setEndChapter(endChapter);
        task.setStatus("PENDING");
        task.setTotalChapters(versions.size());
        task.setCompletedChapters(0);
        task.setFailedChapters(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        recoveryPublisher.publish(task.getId());
        return task;
    }

    @Override
    public void recover(long taskId) {
        if (taskMapper.claim(taskId) != 1) return;
        ContentRecoveryTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        int completed = 0;
        int failed = 0;
        String lastError = null;
        for (BookContentVersion version : versions(task.getCanonicalBookId(), task.getStartChapter(), task.getEndChapter())) {
            try {
                republish(version);
                completed++;
            } catch (Exception exception) {
                failed++;
                lastError = concise(exception);
                log.warn("Could not restore chapter evidence: taskId={}, bookId={}, chapter={}", taskId,
                        task.getCanonicalBookId(), version.getChapterIndex(), exception);
            }
            updateProgress(task, completed, failed, lastError);
        }
        task.setCompletedChapters(completed);
        task.setFailedChapters(failed);
        task.setErrorMessage(lastError);
        task.setStatus(failed == 0 ? "COMPLETED" : (completed == 0 ? "FAILED" : "PARTIAL_FAILED"));
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void republish(BookContentVersion version) {
        if (version.getSourceId() == null || !StringUtils.hasText(version.getChapterUrl())) {
            throw new IllegalStateException("Chapter source metadata is unavailable");
        }
        String content = bookSourceService.getContent(version.getSourceId(), version.getChapterUrl());
        NovelContentNormalizer.Result analysis = NovelContentNormalizer.analyze(content);
        if (!StringUtils.hasText(analysis.normalizedContent())) throw new IllegalStateException("Source returned empty chapter content");
        BookContentVersion active = resolveActiveVersion(version, analysis);
        active.setIndexStatus("PENDING");
        active.setFetchedAt(LocalDateTime.now());
        versionMapper.updateById(active);
        knowledgeIndexPublisher.publish(active.getCanonicalBookId(), active.getChapterIndex(), analysis.normalizedContent(), active.getContentHash());
    }

    private BookContentVersion resolveActiveVersion(BookContentVersion ledger, NovelContentNormalizer.Result analysis) {
        if (analysis.rawHash().equals(ledger.getContentHash())) return ledger;
        BookContentVersion existing = versionMapper.selectOne(Wrappers.<BookContentVersion>lambdaQuery()
                .eq(BookContentVersion::getCanonicalBookId, ledger.getCanonicalBookId())
                .eq(BookContentVersion::getChapterIndex, ledger.getChapterIndex())
                .eq(BookContentVersion::getContentHash, analysis.rawHash()));
        if (existing != null) return existing;
        BookContentVersion replacement = new BookContentVersion();
        replacement.setId(SnowflakeIdUtil.next());
        replacement.setCanonicalBookId(ledger.getCanonicalBookId());
        replacement.setSourceId(ledger.getSourceId());
        replacement.setChapterIndex(ledger.getChapterIndex());
        replacement.setChapterUrl(ledger.getChapterUrl());
        replacement.setContentHash(analysis.rawHash());
        replacement.setRawContentHash(analysis.rawHash());
        replacement.setNormalizedContentHash(analysis.normalizedHash());
        replacement.setSemanticFingerprint(analysis.semanticFingerprint());
        replacement.setQualityScore(analysis.qualityScore());
        replacement.setNormalizationVersion(NovelContentNormalizer.VERSION);
        replacement.setReuseDecision("RECOVERED");
        replacement.setFetchedAt(LocalDateTime.now());
        replacement.setIndexStatus("PENDING");
        versionMapper.insert(replacement);
        return replacement;
    }

    private List<BookContentVersion> versions(long bookId, int start, int end) {
        // A historical ledger can contain multiple snapshots per chapter. The newest source snapshot is recoverable.
        return versionMapper.selectList(Wrappers.<BookContentVersion>lambdaQuery()
                        .eq(BookContentVersion::getCanonicalBookId, bookId)
                        .between(BookContentVersion::getChapterIndex, start, end)
                        .orderByAsc(BookContentVersion::getChapterIndex)
                        .orderByDesc(BookContentVersion::getFetchedAt))
                .stream().collect(java.util.stream.Collectors.toMap(BookContentVersion::getChapterIndex, item -> item,
                        (left, right) -> newer(left, right), java.util.LinkedHashMap::new))
                .values().stream().sorted(Comparator.comparing(BookContentVersion::getChapterIndex)).toList();
    }

    private BookContentVersion newer(BookContentVersion left, BookContentVersion right) {
        LocalDateTime leftFetchedAt = left.getFetchedAt();
        LocalDateTime rightFetchedAt = right.getFetchedAt();
        if (leftFetchedAt == null) return right;
        if (rightFetchedAt == null) return left;
        return rightFetchedAt.isAfter(leftFetchedAt) ? right : left;
    }

    private void updateProgress(ContentRecoveryTask task, int completed, int failed, String error) {
        task.setCompletedChapters(completed);
        task.setFailedChapters(failed);
        task.setErrorMessage(error);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private String concise(Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return message.substring(0, Math.min(message.length(), 1000));
    }
}
