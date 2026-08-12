package com.shanyuefang.novel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.NovelContentNormalizer;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.novel.domain.entity.BookContentVersion;
import com.shanyuefang.novel.domain.entity.BookshelfBook;
import com.shanyuefang.novel.domain.entity.ContentRecoveryTask;
import com.shanyuefang.novel.domain.vo.BookChapterVO;
import com.shanyuefang.novel.mapper.BookContentVersionMapper;
import com.shanyuefang.novel.mapper.BookshelfBookMapper;
import com.shanyuefang.novel.mapper.ContentRecoveryTaskMapper;
import com.shanyuefang.novel.messaging.ContentRecoveryPublisher;
import com.shanyuefang.novel.messaging.KnowledgeIndexPublisher;
import com.shanyuefang.novel.service.BookSourceService;
import com.shanyuefang.novel.service.ContentRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** Restores Agent evidence from the source-side audit ledger without pretending the text still exists locally. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentRecoveryServiceImpl implements ContentRecoveryService {
    private static final int MAX_CHAPTERS_PER_TASK = 200;
    private static final int PREFETCH_CONCURRENCY = 10;

    private final ContentRecoveryTaskMapper taskMapper;
    private final BookContentVersionMapper versionMapper;
    private final BookshelfBookMapper bookshelfMapper;
    private final BookSourceService bookSourceService;
    private final KnowledgeIndexPublisher knowledgeIndexPublisher;
    private final ContentRecoveryPublisher recoveryPublisher;
    @Qualifier("contentRecoveryExecutor")
    private final Executor contentRecoveryExecutor;

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
        task.setTaskType("RECOVERY");
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
    @Transactional(rollbackFor = Exception.class)
    public ContentRecoveryTask enqueuePrefetch(long userId, long canonicalBookId, int startChapter, int endChapter) {
        if (userId <= 0 || canonicalBookId <= 0 || startChapter < 0 || endChapter < startChapter
                || endChapter - startChapter + 1 > MAX_CHAPTERS_PER_TASK) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "一次最多自动补齐 200 章正文");
        }
        BookshelfBook shelfBook = bookshelfMapper.selectOne(Wrappers.<BookshelfBook>lambdaQuery()
                .eq(BookshelfBook::getUserId, userId)
                .eq(BookshelfBook::getCanonicalBookId, canonicalBookId));
        if (shelfBook == null || shelfBook.getSourceId() == null || !StringUtils.hasText(shelfBook.getBookUrl())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "当前书架没有可用书源，无法自动补齐正文");
        }
        ContentRecoveryTask task = new ContentRecoveryTask();
        task.setId(SnowflakeIdUtil.next());
        task.setCanonicalBookId(canonicalBookId); task.setTaskType("PREFETCH");
        task.setRequesterUserId(userId); task.setSourceId(shelfBook.getSourceId()); task.setSourceBookUrl(shelfBook.getBookUrl());
        task.setStartChapter(startChapter); task.setEndChapter(endChapter); task.setStatus("PENDING");
        task.setTotalChapters(endChapter - startChapter + 1); task.setCompletedChapters(0); task.setFailedChapters(0);
        task.setCreatedAt(LocalDateTime.now()); task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        recoveryPublisher.publish(task.getId());
        return task;
    }

    @Override
    public void recover(long taskId) {
        if (taskMapper.claim(taskId) != 1) return;
        ContentRecoveryTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        if ("PREFETCH".equals(task.getTaskType())) {
            prefetch(task);
            return;
        }
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

    @Override
    public ContentRecoveryTask get(long taskId) {
        return taskMapper.selectById(taskId);
    }

    private void prefetch(ContentRecoveryTask task) {
        int completed = 0;
        int failed = 0;
        String lastError = null;
        try {
            Map<Integer, BookChapterVO> chapters = bookSourceService.getChapters(task.getSourceId(), task.getSourceBookUrl()).stream()
                    .filter(chapter -> chapter.getIndex() != null)
                    .collect(java.util.stream.Collectors.toMap(BookChapterVO::getIndex, Function.identity(), (left, right) -> left, LinkedHashMap::new));
            List<Integer> chapterIndexes = java.util.stream.IntStream.rangeClosed(task.getStartChapter(), task.getEndChapter())
                    .boxed().toList();
            for (int offset = 0; offset < chapterIndexes.size(); offset += PREFETCH_CONCURRENCY) {
                List<CompletableFuture<PrefetchResult>> batch = chapterIndexes.subList(offset,
                                Math.min(chapterIndexes.size(), offset + PREFETCH_CONCURRENCY))
                        .stream().map(chapterIndex -> CompletableFuture.supplyAsync(
                                () -> fetchChapter(task, chapters.get(chapterIndex), chapterIndex), contentRecoveryExecutor)).toList();
                for (CompletableFuture<PrefetchResult> future : batch) {
                    PrefetchResult result = future.join();
                    if (result.success()) {
                        completed++;
                    } else {
                        failed++;
                        lastError = result.error();
                        log.warn("Could not prefetch source chapter: taskId={}, bookId={}, chapter={}, reason={}", task.getId(),
                                task.getCanonicalBookId(), result.chapterIndex(), result.error());
                    }
                    updateProgress(task, completed, failed, lastError);
                }
            }
        } catch (Exception exception) {
            failed = Math.max(1, task.getTotalChapters() == null ? 1 : task.getTotalChapters());
            lastError = concise(exception);
            log.warn("Could not load source chapter directory: taskId={}, bookId={}", task.getId(), task.getCanonicalBookId(), exception);
        }
        complete(task, completed, failed, lastError);
    }

    private PrefetchResult fetchChapter(ContentRecoveryTask task, BookChapterVO chapter, int chapterIndex) {
        try {
            if (chapter == null || !StringUtils.hasText(chapter.getChapterUrl())) {
                throw new IllegalStateException("目录中不存在该章节");
            }
            fetchAndPublish(task, chapter);
            return PrefetchResult.success(chapterIndex);
        } catch (Exception exception) {
            return PrefetchResult.failed(chapterIndex, concise(exception));
        }
    }

    private void fetchAndPublish(ContentRecoveryTask task, BookChapterVO chapter) {
        String content = bookSourceService.getContent(task.getSourceId(), chapter.getChapterUrl());
        NovelContentNormalizer.Result analysis = NovelContentNormalizer.analyze(content);
        if (!StringUtils.hasText(analysis.normalizedContent())) throw new IllegalStateException("书源返回了空正文");
        BookContentVersion active = versionMapper.selectOne(Wrappers.<BookContentVersion>lambdaQuery()
                .eq(BookContentVersion::getCanonicalBookId, task.getCanonicalBookId())
                .eq(BookContentVersion::getChapterIndex, chapter.getIndex())
                .eq(BookContentVersion::getContentHash, analysis.rawHash()));
        if (active == null) {
            active = new BookContentVersion();
            active.setId(SnowflakeIdUtil.next()); active.setCanonicalBookId(task.getCanonicalBookId());
            active.setSourceId(task.getSourceId()); active.setChapterIndex(chapter.getIndex()); active.setChapterUrl(chapter.getChapterUrl());
            active.setContentHash(analysis.rawHash()); active.setRawContentHash(analysis.rawHash());
            active.setNormalizedContentHash(analysis.normalizedHash()); active.setSemanticFingerprint(analysis.semanticFingerprint());
            active.setQualityScore(analysis.qualityScore()); active.setNormalizationVersion(NovelContentNormalizer.VERSION);
            active.setReuseDecision("PREFETCHED"); active.setFetchedAt(LocalDateTime.now()); active.setIndexStatus("PENDING");
            versionMapper.insert(active);
        } else {
            active.setIndexStatus("PENDING"); active.setFetchedAt(LocalDateTime.now());
            versionMapper.updateById(active);
        }
        knowledgeIndexPublisher.publish(task.getCanonicalBookId(), chapter.getIndex(), analysis.normalizedContent(), active.getContentHash());
    }

    private void complete(ContentRecoveryTask task, int completed, int failed, String error) {
        task.setCompletedChapters(completed); task.setFailedChapters(failed); task.setErrorMessage(error);
        task.setStatus(failed == 0 ? "COMPLETED" : (completed == 0 ? "FAILED" : "PARTIAL_FAILED"));
        task.setCompletedAt(LocalDateTime.now()); task.setUpdatedAt(LocalDateTime.now());
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

    private record PrefetchResult(int chapterIndex, String error) {
        private static PrefetchResult success(int chapterIndex) { return new PrefetchResult(chapterIndex, null); }
        private static PrefetchResult failed(int chapterIndex, String error) { return new PrefetchResult(chapterIndex, error); }
        private boolean success() { return error == null; }
    }
}
