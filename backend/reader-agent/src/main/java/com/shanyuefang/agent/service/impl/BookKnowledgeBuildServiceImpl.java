package com.shanyuefang.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.config.KnowledgeMessagingConfig;
import com.shanyuefang.agent.domain.dto.StartBookKnowledgeBuildDTO;
import com.shanyuefang.agent.domain.entity.BookKnowledgeBuildTask;
import com.shanyuefang.agent.domain.entity.BookKnowledgeChapterCoverage;
import com.shanyuefang.agent.domain.entity.BookKnowledgeSpace;
import com.shanyuefang.agent.domain.entity.KnowledgeChunk;
import com.shanyuefang.agent.domain.entity.UserModelConfig;
import com.shanyuefang.agent.feign.CreditOperationRequest;
import com.shanyuefang.agent.feign.CommentPublishFeignClient;
import com.shanyuefang.agent.feign.UserCreditFeignClient;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.mapper.BookKnowledgeBuildTaskMapper;
import com.shanyuefang.agent.mapper.BookKnowledgeChapterCoverageMapper;
import com.shanyuefang.agent.mapper.BookKnowledgeSpaceMapper;
import com.shanyuefang.agent.mapper.KnowledgeChunkMapper;
import com.shanyuefang.agent.mapper.KnowledgeGraphNodeMapper;
import com.shanyuefang.agent.mapper.UserModelConfigMapper;
import com.shanyuefang.agent.service.ApiKeyCipher;
import com.shanyuefang.agent.service.BookKnowledgeBuildService;
import com.shanyuefang.agent.service.BookKnowledgeBuildProgressService;
import com.shanyuefang.agent.service.GraphBuildProgressListener;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.agent.service.StructuredGraphExtractor;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.R;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookKnowledgeBuildServiceImpl implements BookKnowledgeBuildService {
    private static final String PLATFORM = "PLATFORM";
    private static final String BYOK = "BYOK";
    /** Avoid resolving the same work name on every two-second task-list refresh. */
    private final ConcurrentMap<Long, String> bookTitles = new ConcurrentHashMap<>();
    private final BookKnowledgeBuildTaskMapper taskMapper;
    private final BookKnowledgeChapterCoverageMapper coverageMapper;
    private final BookKnowledgeSpaceMapper spaceMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeGraphNodeMapper nodeMapper;
    private final UserModelConfigMapper modelConfigMapper;
    private final ApiKeyCipher apiKeyCipher;
    private final AgentProperties properties;
    private final KnowledgeService knowledgeService;
    private final UserCreditFeignClient creditClient;
    private final CommentPublishFeignClient commentPublishClient;
    private final CanonicalBookFeignClient canonicalBookClient;
    private final RabbitTemplate rabbitTemplate;
    /** Non-final for compatibility with focused constructor-based unit tests. */
    @org.springframework.beans.factory.annotation.Autowired
    private BookKnowledgeBuildProgressService progressService;

    /** Requeue durable work left between delivery and acknowledgement, and fail only a genuinely interrupted worker. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        List<BookKnowledgeBuildTask> interrupted = taskMapper.selectList(Wrappers.<BookKnowledgeBuildTask>lambdaQuery()
                .in(BookKnowledgeBuildTask::getStatus, "QUEUED", "RUNNING"));
        for (BookKnowledgeBuildTask task : interrupted) {
            if ("QUEUED".equals(task.getStatus())) {
                publish(task.getId());
                continue;
            }
            task.setStatus("FAILED");
            task.setMessage("服务重启导致构建中断，已停止任务，可重新发起构建");
            task.setErrorMessage("服务重启导致构建中断，已停止任务，可重新发起构建");
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            updateSpace(task, "FAILED", task.getCompletedChapters() == null ? 0 : task.getCompletedChapters(), task.getErrorMessage());
            if (PLATFORM.equals(task.getModelMode()) && task.getChargedCredits() != null && task.getChargedCredits() > 0) refund(task);
        }
    }

    /** Repairs the commit-to-publish gap: a durable queued row is re-published until a consumer claims it. */
    @Scheduled(fixedDelayString = "${AGENT_GRAPH_BUILD_QUEUE_RECONCILE_MILLIS:30000}")
    public void republishQueuedTasks() {
        taskMapper.selectList(Wrappers.<BookKnowledgeBuildTask>lambdaQuery().eq(BookKnowledgeBuildTask::getStatus, "QUEUED"))
                .forEach(task -> publish(task.getId()));
    }

    @Override
    public Map<String, Object> prepare(long userId, long canonicalBookId) {
        return prepare(userId, canonicalBookId, null, null);
    }

    @Override
    public Map<String, Object> prepare(long userId, long canonicalBookId, Integer startChapter, Integer endChapter) {
        BookKnowledgeSpace existing = spaceMapper.selectById(canonicalBookId);
        ChapterRange requestedRange = resolveRange(canonicalBookId, startChapter, endChapter);
        ChapterRange uncoveredRange = uncoveredRange(canonicalBookId, requestedRange);
        Estimate estimate = estimate(canonicalBookId, uncoveredRange);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canonicalBookId", canonicalBookId);
        result.put("status", existing == null ? "NOT_BUILT" : existing.getStatus());
        result.put("isPublic", existing == null || Boolean.TRUE.equals(existing.getIsPublic()));
        result.put("isOwner", existing != null && Long.valueOf(userId).equals(existing.getOwnerUserId()));
        result.put("totalChapters", requestedRange.availableChapters());
        result.put("startChapter", requestedRange.startChapter());
        result.put("endChapter", requestedRange.endChapter());
        result.put("selectedChapters", estimate.chapters());
        result.put("coveredChapters", coveredChapterCount(canonicalBookId, requestedRange));
        result.put("rangeCovered", estimate.chapters() == 0);
        result.put("estimatedInputTokens", estimate.inputTokens());
        result.put("estimatedOutputTokens", estimate.outputTokens());
        result.put("estimatedCredits", estimate.credits());
        result.put("creditRule", "平台模型按约每 2000 Token 折算 1 积分；实际模型账单与平台积分并非一比一关系。");
        result.put("requiresBuild", estimate.chapters() > 0);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookKnowledgeBuildTask start(long userId, long canonicalBookId, StartBookKnowledgeBuildDTO dto) {
        String mode = normalizeMode(dto.getModelMode());
        ChapterRange range = uncoveredRange(canonicalBookId, resolveRange(canonicalBookId, dto.getStartChapter(), dto.getEndChapter()));
        Estimate estimate = estimate(canonicalBookId, range);
        if (estimate.chapters() == 0) throw new BusinessException(ResultCode.PARAM_ERROR, "所选章节均已完成知识图谱构建，无需重复消耗积分。");
        BookKnowledgeSpace space = spaceMapper.selectById(canonicalBookId);
        if (space != null && space.getOwnerUserId() != null && !Long.valueOf(userId).equals(space.getOwnerUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该知识图谱由其他用户创建，你可以使用公开图谱但不能覆盖它");
        }
        if (space != null && ("QUEUED".equals(space.getStatus()) || "RUNNING".equals(space.getStatus()))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "这本书的知识图谱正在构建，请在任务中心查看进度。");
        }
        if (BYOK.equals(mode)) requireOwnedModel(userId, dto.getModelConfigId());
        BookKnowledgeBuildTask task = new BookKnowledgeBuildTask();
        task.setId(SnowflakeIdUtil.next()); task.setCanonicalBookId(canonicalBookId); task.setRequesterUserId(userId);
        task.setModelMode(mode); task.setModelConfigId(BYOK.equals(mode) ? dto.getModelConfigId() : null);
        task.setIsPublic(!Boolean.FALSE.equals(dto.getSharePublic())); task.setStatus("QUEUED");
        task.setStartChapter(range.startChapter()); task.setEndChapter(range.endChapter());
        task.setTotalChapters(estimate.chapters()); task.setCompletedChapters(0);
        task.setCurrentStage("EXTRACT"); task.setStageCompletedUnits(0); task.setStageTotalUnits(estimate.chapters()); task.setOverallProgress(0);
        task.setEstimatedInputTokens(estimate.inputTokens()); task.setEstimatedOutputTokens(estimate.outputTokens());
        task.setEstimatedCredits(PLATFORM.equals(mode) ? estimate.credits() : 0); task.setChargedCredits(0);
        task.setMessage("等待开始"); task.setCreatedAt(LocalDateTime.now()); task.setUpdatedAt(LocalDateTime.now()); taskMapper.insert(task);
        BookKnowledgeSpace saved = space == null ? new BookKnowledgeSpace() : space;
        saved.setCanonicalBookId(canonicalBookId); saved.setStatus("QUEUED"); saved.setIsPublic(task.getIsPublic()); saved.setOwnerUserId(userId);
        saved.setModelMode(mode); saved.setModelConfigId(task.getModelConfigId()); saved.setTotalChapters(estimate.chapters()); saved.setCompletedChapters(0);
        saved.setEstimatedInputTokens(estimate.inputTokens()); saved.setEstimatedOutputTokens(estimate.outputTokens()); saved.setEstimatedCredits(task.getEstimatedCredits());
        saved.setFailureMessage(null); saved.setUpdatedAt(LocalDateTime.now()); if (space == null) { saved.setCreatedAt(LocalDateTime.now()); spaceMapper.insert(saved); } else spaceMapper.updateById(saved);
        // Publish only after the task row commits so a consumer can always resolve its payload from durable storage.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { publish(task.getId()); }
        });
        return task;
    }

    @Override public List<BookKnowledgeBuildTask> myTasks(long userId, int limit) {
        List<BookKnowledgeBuildTask> tasks = taskMapper.selectList(Wrappers.<BookKnowledgeBuildTask>lambdaQuery()
                .eq(BookKnowledgeBuildTask::getRequesterUserId, userId)
                .orderByDesc(BookKnowledgeBuildTask::getUpdatedAt).last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        tasks.forEach(task -> task.setBookTitle(resolveBookTitle(task.getCanonicalBookId())));
        return tasks;
    }

    private String resolveBookTitle(Long canonicalBookId) {
        if (canonicalBookId == null) return null;
        String cachedTitle = bookTitles.get(canonicalBookId);
        if (cachedTitle != null) return cachedTitle;
        try {
            R<Map<String, Object>> response = canonicalBookClient.detail(properties.getInternalToken(), canonicalBookId);
            Object title = response == null || response.getData() == null ? null : response.getData().get("title");
            if (title == null || !StringUtils.hasText(String.valueOf(title))) return "未知作品";
            String resolvedTitle = String.valueOf(title);
            bookTitles.putIfAbsent(canonicalBookId, resolvedTitle);
            return resolvedTitle;
        } catch (Exception ignored) {
            // A temporary novel-service failure must not make the task center unavailable.
            return "未知作品";
        }
    }

    @Override
    public void deleteTask(long userId, long taskId) {
        BookKnowledgeBuildTask task = taskMapper.selectById(taskId);
        if (task == null || !Long.valueOf(userId).equals(task.getRequesterUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未找到该构建任务");
        }
        if ("QUEUED".equals(task.getStatus()) || "RUNNING".equals(task.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "构建中的任务不能删除，请等待它结束后再清理记录");
        }
        if (taskMapper.deleteOwnedTask(userId, taskId) != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该构建任务已被删除");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSharing(long userId, long canonicalBookId, boolean isPublic) {
        BookKnowledgeSpace space = requireOwnedSpace(userId, canonicalBookId);
        if ("QUEUED".equals(space.getStatus()) || "RUNNING".equals(space.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "图谱构建中，完成后才能修改共享范围");
        }
        space.setIsPublic(isPublic); space.setUpdatedAt(LocalDateTime.now()); spaceMapper.updateById(space);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnedGraph(long userId, long canonicalBookId) {
        BookKnowledgeSpace space = requireOwnedSpace(userId, canonicalBookId);
        if ("QUEUED".equals(space.getStatus()) || "RUNNING".equals(space.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "图谱构建中，不能删除");
        }
        // "Delete knowledge graph" removes only graph-derived claims. Chapter evidence is the reusable
        // retrieval foundation and must remain available for a later graph rebuild.
        knowledgeService.clearGraph(canonicalBookId);
        coverageMapper.delete(Wrappers.<BookKnowledgeChapterCoverage>lambdaQuery()
                .eq(BookKnowledgeChapterCoverage::getCanonicalBookId, canonicalBookId));
        space.setStatus("NOT_BUILT"); space.setCompletedChapters(0); space.setFailureMessage(null);
        space.setUpdatedAt(LocalDateTime.now()); spaceMapper.updateById(space);
    }

    @Override
    public void ensureReadable(long userId, long canonicalBookId) {
        BookKnowledgeSpace space = spaceMapper.selectById(canonicalBookId);
        if (space != null && !Boolean.TRUE.equals(space.getIsPublic())
                && !Long.valueOf(userId).equals(space.getOwnerUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "这本书的知识图谱仅创建者可见");
        }
    }

    private BookKnowledgeSpace requireOwnedSpace(long userId, long canonicalBookId) {
        BookKnowledgeSpace space = spaceMapper.selectById(canonicalBookId);
        if (space == null || !Long.valueOf(userId).equals(space.getOwnerUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有创建者可以管理这本书的知识图谱");
        }
        return space;
    }

    @Override public Map<Long, Map<String, Object>> statuses(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return spaceMapper.selectList(Wrappers.<BookKnowledgeSpace>lambdaQuery().in(BookKnowledgeSpace::getCanonicalBookId, ids)).stream()
                .collect(Collectors.toMap(BookKnowledgeSpace::getCanonicalBookId, this::statusMap, (a, b) -> a, LinkedHashMap::new));
    }
    @Override public Map<String, Object> status(long canonicalBookId) {
        BookKnowledgeSpace space = spaceMapper.selectById(canonicalBookId);
        return space == null ? Map.of("status", "NOT_BUILT", "isPublic", true) : statusMap(space);
    }

    @Override @Transactional(rollbackFor = Exception.class) public void markCleared(long canonicalBookId) {
        BookKnowledgeSpace space = spaceMapper.selectById(canonicalBookId);
        if (space == null) { space = new BookKnowledgeSpace(); space.setCanonicalBookId(canonicalBookId); space.setCreatedAt(LocalDateTime.now()); }
        if (space.getIsPublic() == null) space.setIsPublic(true);
        space.setStatus("NOT_BUILT"); space.setCompletedChapters(0); space.setFailureMessage(null); space.setUpdatedAt(LocalDateTime.now());
        if (spaceMapper.selectById(canonicalBookId) == null) spaceMapper.insert(space); else spaceMapper.updateById(space);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void synchronizeCompletedRange(long canonicalBookId, int startChapter, int endChapter, boolean replaceExisting) {
        if (startChapter < 1 || endChapter < startChapter) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "知识图谱章节覆盖范围无效");
        }
        if (replaceExisting) {
            coverageMapper.delete(Wrappers.<BookKnowledgeChapterCoverage>lambdaQuery()
                    .eq(BookKnowledgeChapterCoverage::getCanonicalBookId, canonicalBookId));
        }
        List<Integer> indexedChapters = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId)
                        .between(KnowledgeChunk::getChapterIndex, startChapter - 1, endChapter - 1))
                .stream().map(KnowledgeChunk::getChapterIndex).filter(java.util.Objects::nonNull).distinct().toList();
        if (indexedChapters.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "所选范围没有可同步的章节正文索引");
        }
        recordCoverage(canonicalBookId, indexedChapters);
        publishSynchronizedSpace(canonicalBookId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void synchronizeAllIndexedChapters(long canonicalBookId, boolean replaceExisting) {
        List<Integer> indexedChapters = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getCanonicalBookId, canonicalBookId))
                .stream().map(KnowledgeChunk::getChapterIndex).filter(java.util.Objects::nonNull).distinct().toList();
        if (indexedChapters.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "这本书没有可同步的章节正文索引");
        }
        if (replaceExisting) {
            coverageMapper.delete(Wrappers.<BookKnowledgeChapterCoverage>lambdaQuery()
                    .eq(BookKnowledgeChapterCoverage::getCanonicalBookId, canonicalBookId));
        }
        recordCoverage(canonicalBookId, indexedChapters);
        publishSynchronizedSpace(canonicalBookId);
    }

    private void publishSynchronizedSpace(long canonicalBookId) {
        int covered = totalCoveredChapterCount(canonicalBookId);
        BookKnowledgeSpace space = spaceMapper.selectById(canonicalBookId);
        boolean insert = space == null;
        if (insert) {
            space = new BookKnowledgeSpace();
            space.setCanonicalBookId(canonicalBookId);
            space.setIsPublic(true);
            space.setModelMode(PLATFORM);
            space.setCreatedAt(LocalDateTime.now());
        }
        space.setStatus("READY");
        space.setTotalChapters(covered);
        space.setCompletedChapters(covered);
        space.setFailureMessage(null);
        space.setUpdatedAt(LocalDateTime.now());
        if (insert) spaceMapper.insert(space); else spaceMapper.updateById(space);
    }

    @Override
    public boolean consumeQueuedTask(long taskId) {
        if (taskMapper.claimQueuedTask(taskId) != 1) return false;
        BookKnowledgeBuildTask task = taskMapper.selectById(taskId);
        if (task == null) return false;
        try {
            task.setStatus("RUNNING"); task.setMessage("正在准备章节实体与关系抽取"); task.setUpdatedAt(LocalDateTime.now()); taskMapper.updateById(task); updateSpace(task, "RUNNING", 0, null);
            if (PLATFORM.equals(task.getModelMode())) freeze(task);
            StructuredGraphExtractor.ModelConfig config = modelConfig(task);
            knowledgeService.buildGraphRangeWithProgress(task.getCanonicalBookId(), task.getStartChapter(), task.getEndChapter(), config,
                    progressListener(task));
            recordStage(task, GraphBuildProgressListener.Stage.FINALIZE, 0, 1, "正在核验图谱结果并保存构建记录");
            synchronizeCompletedRange(task.getCanonicalBookId(), task.getStartChapter(), task.getEndChapter(), false);
            long graphClaims = nodeMapper.selectCount(Wrappers.<com.shanyuefang.agent.domain.entity.KnowledgeGraphNode>lambdaQuery()
                    .eq(com.shanyuefang.agent.domain.entity.KnowledgeGraphNode::getCanonicalBookId, task.getCanonicalBookId()));
            if (graphClaims == 0) throw new IllegalStateException("模型未返回可验证的实体或关系，未发布空知识图谱");
            task.setStatus("COMPLETED"); task.setCompletedChapters(task.getTotalChapters()); task.setCurrentStage("FINALIZE");
            task.setStageCompletedUnits(1); task.setStageTotalUnits(1); task.setOverallProgress(100); task.setMessage("知识图谱已构建完成"); task.setCompletedAt(LocalDateTime.now()); task.setUpdatedAt(LocalDateTime.now()); taskMapper.updateById(task);
            if (PLATFORM.equals(task.getModelMode())) settle(task);
            if (Boolean.TRUE.equals(task.getIsPublic())) publishShare(task);
        } catch (Exception exception) {
            // Per-chapter progress is committed independently; reload it before recording a terminal
            // failure so an in-memory task object cannot overwrite the visible count with zero.
            BookKnowledgeBuildTask latest = taskMapper.selectById(task.getId());
            if (latest != null && latest.getCompletedChapters() != null) task.setCompletedChapters(latest.getCompletedChapters());
            task.setStatus("FAILED"); task.setErrorMessage(safeMessage(exception));
            task.setMessage("构建失败：" + task.getErrorMessage());
            task.setCompletedAt(LocalDateTime.now()); task.setUpdatedAt(LocalDateTime.now()); taskMapper.updateById(task); updateSpace(task, "FAILED", task.getCompletedChapters(), task.getErrorMessage());
            if (PLATFORM.equals(task.getModelMode()) && task.getChargedCredits() != null && task.getChargedCredits() > 0) refund(task);
        }
        return true;
    }

    private void publish(long taskId) {
        rabbitTemplate.convertAndSend(KnowledgeMessagingConfig.EXCHANGE,
                KnowledgeMessagingConfig.GRAPH_BUILD_ROUTING_KEY, Map.of("taskId", taskId));
    }

    private void updateProgress(BookKnowledgeBuildTask task, int completed) {
        task.setCompletedChapters(completed);
        String message = completed >= task.getTotalChapters()
                ? "已完成 " + completed + " / " + task.getTotalChapters() + " 章的大模型关系抽取"
                : "已完成 " + completed + " / " + task.getTotalChapters() + " 章，正在分析第 " + (task.getStartChapter() + completed) + " 章";
        task.setCurrentStage("EXTRACT"); task.setStageCompletedUnits(completed); task.setStageTotalUnits(task.getTotalChapters());
        task.setOverallProgress(Math.min(70, Math.round(completed * 70f / Math.max(1, task.getTotalChapters()))));
        task.setMessage(message); task.setUpdatedAt(LocalDateTime.now());
        // buildGraphRange holds one transaction for graph consistency. Persist this small task update in
        // an independent transaction so the two-second UI poll does not wait for every chapter to finish.
        if (progressService != null) {
            progressService.record(task.getId(), task.getCanonicalBookId(), task.getTotalChapters(),
                    task.getStartChapter(), completed);
        } else {
            taskMapper.updateById(task);
            updateSpace(task, "RUNNING", completed, null);
        }
    }

    private GraphBuildProgressListener progressListener(BookKnowledgeBuildTask task) {
        return new GraphBuildProgressListener() {
            @Override public void chapterExtracted(int completedChapters) {
                updateProgress(task, completedChapters);
            }

            @Override public void stageStarted(Stage stage) {
                recordStage(task, stage, 0, stageUnits(stage, task), stageMessage(stage));
            }

            @Override public void stageProgress(Stage stage, int completedUnits, int totalUnits) {
                recordStage(task, stage, completedUnits, totalUnits, stageMessage(stage));
            }

            @Override public void stageCompleted(Stage stage) {
                recordStage(task, stage, stageUnits(stage, task), stageUnits(stage, task), stageCompletedMessage(stage));
            }
        };
    }

    private void recordStage(BookKnowledgeBuildTask task, GraphBuildProgressListener.Stage stage,
                             int completedUnits, int totalUnits, String message) {
        task.setCurrentStage(stage.name()); task.setStageCompletedUnits(completedUnits); task.setStageTotalUnits(totalUnits);
        task.setOverallProgress(stageProgress(stage, completedUnits, totalUnits));
        task.setMessage(message); task.setUpdatedAt(LocalDateTime.now());
        if (progressService != null) {
            progressService.recordStage(task.getId(), stage.name(), completedUnits, totalUnits, message);
        } else {
            taskMapper.updateById(task);
        }
    }

    private int stageUnits(GraphBuildProgressListener.Stage stage, BookKnowledgeBuildTask task) {
        return stage == GraphBuildProgressListener.Stage.EXTRACT ? Math.max(1, task.getTotalChapters())
                : stage == GraphBuildProgressListener.Stage.RAG_REFRESH ? 2 : 1;
    }

    private int stageProgress(GraphBuildProgressListener.Stage stage, int completedUnits, int totalUnits) {
        int ratio = Math.min(100, Math.round(Math.max(0, completedUnits) * 100f / Math.max(1, totalUnits)));
        return switch (stage) {
            case EXTRACT -> Math.round(ratio * .70f);
            case CHARACTER_CALIBRATION -> 70 + Math.round(ratio * .08f);
            case STORY_EVENTS -> 78 + Math.round(ratio * .06f);
            case CLUE_SYNTHESIS -> 84 + Math.round(ratio * .05f);
            case CLUE_LIFECYCLE -> 89 + Math.round(ratio * .04f);
            case RAG_REFRESH -> 93 + Math.round(ratio * .04f);
            case GRAPH_PROJECTION -> 97 + Math.round(ratio * .02f);
            case FINALIZE -> 99;
        };
    }

    private String stageMessage(GraphBuildProgressListener.Stage stage) {
        return switch (stage) {
            case EXTRACT -> "正在提取章节中的实体与关系";
            case CHARACTER_CALIBRATION -> "正在校准人物身份与关系";
            case STORY_EVENTS -> "正在归纳剧情事件脉络";
            case CLUE_SYNTHESIS -> "正在识别并关联故事线索";
            case CLUE_LIFECYCLE -> "正在核对线索的推进与揭晓状态";
            case RAG_REFRESH -> "正在刷新作品画像与检索索引";
            case GRAPH_PROJECTION -> "正在同步知识图谱展示数据";
            case FINALIZE -> "正在核验图谱结果并保存构建记录";
        };
    }

    private String stageCompletedMessage(GraphBuildProgressListener.Stage stage) {
        return switch (stage) {
            case EXTRACT -> "章节实体与关系抽取完成，开始整合图谱";
            case CHARACTER_CALIBRATION -> "人物身份与关系校准完成";
            case STORY_EVENTS -> "剧情事件脉络归纳完成";
            case CLUE_SYNTHESIS -> "故事线索识别完成";
            case CLUE_LIFECYCLE -> "线索生命周期核对完成";
            case RAG_REFRESH -> "作品画像与检索索引刷新完成";
            case GRAPH_PROJECTION -> "知识图谱展示数据同步完成";
            case FINALIZE -> "图谱结果核验完成";
        };
    }
    private void recordCoverage(long canonicalBookId, List<Integer> chapterIndexes) {
        for (Integer chapterIndex : chapterIndexes) {
            BookKnowledgeChapterCoverage coverage = new BookKnowledgeChapterCoverage();
            coverage.setCanonicalBookId(canonicalBookId); coverage.setChapterIndex(chapterIndex); coverage.setCompletedAt(LocalDateTime.now());
            coverageMapper.insertIfAbsent(coverage);
        }
    }
    private int totalCoveredChapterCount(long canonicalBookId) {
        Long count = coverageMapper.selectCount(Wrappers.<BookKnowledgeChapterCoverage>lambdaQuery()
                .eq(BookKnowledgeChapterCoverage::getCanonicalBookId, canonicalBookId));
        return count == null ? 0 : count.intValue();
    }
    private void updateSpace(BookKnowledgeBuildTask task, String status, int completed, String error) {
        BookKnowledgeSpace space = spaceMapper.selectById(task.getCanonicalBookId()); if (space == null) return;
        space.setStatus(status); space.setCompletedChapters(completed); space.setFailureMessage(error); space.setUpdatedAt(LocalDateTime.now()); spaceMapper.updateById(space);
    }
    private void freeze(BookKnowledgeBuildTask task) { credit("freeze", task, task.getEstimatedCredits(), "知识图谱构建预授权"); task.setChargedCredits(task.getEstimatedCredits()); taskMapper.updateById(task); }
    private void settle(BookKnowledgeBuildTask task) { credit("settle", task, task.getChargedCredits(), "知识图谱构建完成"); }
    private void refund(BookKnowledgeBuildTask task) { try { credit("refund", task, task.getChargedCredits(), "知识图谱构建失败退回"); } catch (Exception ignored) { } }
    private void credit(String action, BookKnowledgeBuildTask task, int amount, String reason) {
        if (amount <= 0) return; CreditOperationRequest request = new CreditOperationRequest(); request.setUserId(task.getRequesterUserId()); request.setAmount(amount); request.setRequestId("book-knowledge-" + action + "-" + task.getId()); request.setReason(reason);
        R<Void> result = switch (action) { case "freeze" -> creditClient.freeze(request); case "settle" -> creditClient.settle(request); default -> creditClient.refund(request); };
        if (result == null || result.getCode() != 200) throw new IllegalStateException("积分服务未确认本次构建扣费");
    }
    private StructuredGraphExtractor.ModelConfig modelConfig(BookKnowledgeBuildTask task) {
        if (BYOK.equals(task.getModelMode())) { UserModelConfig config = requireOwnedModel(task.getRequesterUserId(), task.getModelConfigId()); return new StructuredGraphExtractor.ModelConfig(config.getProvider(), config.getModel(), config.getBaseUrl(), apiKeyCipher.decrypt(config.getEncryptedApiKey())); }
        if (!StringUtils.hasText(properties.getPlatformApiKey())) throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "平台模型尚未配置");
        return new StructuredGraphExtractor.ModelConfig(properties.getPlatformProvider(), properties.getPlatformModel(), properties.getPlatformBaseUrl(), properties.getPlatformApiKey());
    }
    private void publishShare(BookKnowledgeBuildTask task) {
        try {
            R<Map<String, Object>> detail = canonicalBookClient.detail(properties.getInternalToken(), task.getCanonicalBookId());
            String title = detail == null || detail.getData() == null ? "这本书" : String.valueOf(detail.getData().getOrDefault("title", "这本书"));
            commentPublishClient.publish(task.getRequesterUserId(), Map.of(
                    "novelId", task.getCanonicalBookId(),
                    "bookTitle", title,
                    "activityType", "KNOWLEDGE_GRAPH_BUILD",
                    "content", "我构建了《" + title + "》第 " + task.getStartChapter()
                            + " 章到第 " + task.getEndChapter() + " 章的知识图谱。"));
        } catch (Exception ignored) {
            // Sharing is a social side effect; a temporary square outage must not fail the completed index.
        }
    }
    private UserModelConfig requireOwnedModel(long userId, Long configId) {
        UserModelConfig config = configId == null ? null : modelConfigMapper.selectById(configId);
        if (config == null || !config.getUserId().equals(userId) || !Boolean.TRUE.equals(config.getEnabled()) || Boolean.TRUE.equals(config.getDeleted())) throw new BusinessException(ResultCode.PARAM_ERROR, "请选择一个已启用的个人模型");
        return config;
    }
    private Estimate estimate(long bookId, ChapterRange range) {
        if (range.endChapter() < range.startChapter()) return new Estimate(0, 0L, 0L, 0);
        List<KnowledgeChunk> chunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getCanonicalBookId, bookId)
                .between(KnowledgeChunk::getChapterIndex, range.startChapter() - 1, range.endChapter() - 1));
        int chapters = (int) chunks.stream().map(KnowledgeChunk::getChapterIndex).distinct().count(); long chars = chunks.stream().mapToLong(item -> item.getContent() == null ? 0 : item.getContent().length()).sum();
        long input = Math.max(chapters, chars / 3); long output = Math.max(0, chapters * 500L); int credits = (int) Math.max(1, Math.ceil((input + output) / 2000.0D)); return new Estimate(chapters, input, output, credits);
    }
    private ChapterRange resolveRange(long bookId, Integer requestedStart, Integer requestedEnd) {
        List<Integer> indexes = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getCanonicalBookId, bookId).orderByAsc(KnowledgeChunk::getChapterIndex))
                .stream().map(KnowledgeChunk::getChapterIndex).filter(java.util.Objects::nonNull).distinct().sorted().toList();
        if (indexes.isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR, "这本书还没有可用于构建知识图谱的章节内容，请先打开并加载章节。");
        int maxChapter = indexes.get(indexes.size() - 1) + 1;
        int start = requestedStart == null ? 1 : requestedStart;
        int end = requestedEnd == null ? maxChapter : requestedEnd;
        if (start < 1 || end < start || end > maxChapter) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择第 1 章到第 " + maxChapter + " 章之间的有效范围");
        }
        return new ChapterRange(start, end, indexes.size());
    }
    private ChapterRange uncoveredRange(long bookId, ChapterRange requested) {
        List<BookKnowledgeChapterCoverage> rows = coverageMapper.selectList(Wrappers.<BookKnowledgeChapterCoverage>lambdaQuery()
                        .eq(BookKnowledgeChapterCoverage::getCanonicalBookId, bookId)
                        .between(BookKnowledgeChapterCoverage::getChapterIndex, requested.startChapter() - 1, requested.endChapter() - 1));
        List<Integer> covered = (rows == null ? List.<BookKnowledgeChapterCoverage>of() : rows).stream()
                .map(BookKnowledgeChapterCoverage::getChapterIndex).toList();
        int first = requested.startChapter();
        while (first <= requested.endChapter() && covered.contains(first - 1)) first++;
        if (first > requested.endChapter()) return new ChapterRange(first, first - 1, requested.availableChapters());
        // A task represents one continuous missing segment. If a reader built a
        // later interval first, do not silently re-charge already covered chapters.
        int end = first;
        while (end < requested.endChapter() && !covered.contains(end)) end++;
        return new ChapterRange(first, end, requested.availableChapters());
    }
    private int coveredChapterCount(long bookId, ChapterRange range) {
        if (range.endChapter() < range.startChapter()) return 0;
        Long count = coverageMapper.selectCount(Wrappers.<BookKnowledgeChapterCoverage>lambdaQuery()
                .eq(BookKnowledgeChapterCoverage::getCanonicalBookId, bookId)
                .between(BookKnowledgeChapterCoverage::getChapterIndex, range.startChapter() - 1, range.endChapter() - 1));
        return count == null ? 0 : count.intValue();
    }
    private String normalizeMode(String value) { String mode = value == null ? PLATFORM : value.trim().toUpperCase(Locale.ROOT); if (!PLATFORM.equals(mode) && !BYOK.equals(mode)) throw new BusinessException(ResultCode.PARAM_ERROR, "模型来源只能选择平台模型或个人模型"); return mode; }
    private Map<String, Object> statusMap(BookKnowledgeSpace space) { return Map.of("status", space.getStatus(), "isPublic", Boolean.TRUE.equals(space.getIsPublic()), "totalChapters", space.getTotalChapters(), "completedChapters", space.getCompletedChapters(), "ownerUserId", space.getOwnerUserId() == null ? 0L : space.getOwnerUserId()); }
    private String safeMessage(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            String causeMessage = cause.getMessage();
            if (cause instanceof java.net.SocketTimeoutException || (causeMessage != null && causeMessage.toLowerCase(Locale.ROOT).contains("timed out"))) {
                return "模型服务响应超时，构建已停止且平台积分会自动退回，请稍后重试";
            }
            cause = cause.getCause();
        }
        String value = exception.getMessage() == null ? "模型调用或图谱写入失败" : exception.getMessage();
        if (value.contains("未能返回可验证的知识图谱 JSON")) {
            return "平台模型输出格式不完整或未提供可验证的原文证据，请缩小章节范围后重试";
        }
        return value.substring(0, Math.min(value.length(), 1000));
    }
    private record Estimate(int chapters, long inputTokens, long outputTokens, int credits) { }
    private record ChapterRange(int startChapter, int endChapter, int availableChapters) { }
}
