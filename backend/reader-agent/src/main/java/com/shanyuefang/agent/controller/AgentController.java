package com.shanyuefang.agent.controller;

import com.shanyuefang.agent.domain.dto.ChatMessageDTO;
import com.shanyuefang.agent.domain.dto.CreateSessionDTO;
import com.shanyuefang.agent.domain.dto.RenameAgentSessionDTO;
import com.shanyuefang.agent.domain.dto.UpdateAgentMessageDTO;
import com.shanyuefang.agent.domain.dto.SaveModelConfigDTO;
import com.shanyuefang.agent.domain.dto.SaveAgentPreferenceDTO;
import com.shanyuefang.agent.domain.dto.RecommendationFeedbackDTO;
import com.shanyuefang.agent.domain.dto.SaveShelfGroupDTO;
import com.shanyuefang.agent.domain.vo.ClueVO;
import com.shanyuefang.agent.domain.vo.KnowledgeGraphVO;
import com.shanyuefang.agent.domain.vo.PlotCapsuleVO;
import com.shanyuefang.agent.domain.vo.UserAgentPreferenceVO;
import com.shanyuefang.agent.domain.vo.AgentMessageVO;
import com.shanyuefang.agent.domain.vo.AgentReplyVO;
import com.shanyuefang.agent.domain.vo.AgentSessionVO;
import com.shanyuefang.agent.domain.vo.UserModelConfigVO;
import com.shanyuefang.agent.domain.vo.SimilarBookVO;
import com.shanyuefang.agent.domain.vo.ReadingMapVO;
import com.shanyuefang.agent.domain.vo.ReadingPlanVO;
import com.shanyuefang.agent.service.AgentService;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.agent.service.RecommendationService;
import com.shanyuefang.agent.service.AgentPreferenceService;
import com.shanyuefang.agent.service.RecommendationFeedbackService;
import com.shanyuefang.agent.service.SpoilerBoundaryService;
import com.shanyuefang.agent.service.ShelfGroupService;
import com.shanyuefang.agent.service.ModelRouteService;
import com.shanyuefang.agent.service.BookKnowledgeBuildService;
import com.shanyuefang.agent.domain.dto.StartBookKnowledgeBuildDTO;
import com.shanyuefang.agent.domain.entity.BookKnowledgeBuildTask;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentController {
    private final AgentService agentService;
    private final KnowledgeService knowledgeService;
    private final RecommendationService recommendationService;
    private final AgentProperties agentProperties;
    private final AgentPreferenceService preferenceService;
    private final RecommendationFeedbackService feedbackService;
    private final CanonicalBookFeignClient canonicalBookClient;
    private final SpoilerBoundaryService spoilerBoundaryService;
    private final ShelfGroupService shelfGroupService;
    private final ModelRouteService modelRouteService;
    private final BookKnowledgeBuildService bookKnowledgeBuildService;

    @PostMapping("/sessions")
    public R<AgentSessionVO> createSession(@RequestHeader("X-User-Id") Long userId,
                                            @Valid @RequestBody CreateSessionDTO dto) {
        return R.ok(agentService.createSession(userId, dto));
    }

    @GetMapping("/sessions")
    public R<List<AgentSessionVO>> listSessions(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(agentService.listSessions(userId));
    }

    @GetMapping("/sessions/search")
    public R<List<AgentSessionVO>> searchSessions(@RequestHeader("X-User-Id") Long userId,
                                                    @RequestParam String keyword) {
        return R.ok(agentService.searchSessions(userId, keyword));
    }

    @PutMapping("/sessions/{sessionId}/title")
    public R<AgentSessionVO> renameSession(@RequestHeader("X-User-Id") Long userId,
                                            @PathVariable Long sessionId,
                                            @Valid @RequestBody RenameAgentSessionDTO dto) {
        return R.ok(agentService.renameSession(userId, sessionId, dto.getTitle()));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<AgentMessageVO>> listMessages(@RequestHeader("X-User-Id") Long userId,
                                                  @PathVariable Long sessionId) {
        return R.ok(agentService.listMessages(userId, sessionId));
    }

    @PutMapping("/sessions/{sessionId}/messages/{messageId}")
    public R<Void> updateUserMessage(@RequestHeader("X-User-Id") Long userId,
                                     @PathVariable Long sessionId,
                                     @PathVariable Long messageId,
                                     @Valid @RequestBody UpdateAgentMessageDTO dto) {
        agentService.updateUserMessage(userId, sessionId, messageId, dto.getContent());
        return R.ok();
    }

    @GetMapping("/sessions/{sessionId}/export")
    public R<Map<String, Object>> exportSession(@RequestHeader("X-User-Id") Long userId,
                                                 @PathVariable Long sessionId) {
        return R.ok(agentService.exportSession(userId, sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public R<Void> deleteSession(@RequestHeader("X-User-Id") Long userId,
                                 @PathVariable Long sessionId) {
        agentService.deleteSession(userId, sessionId);
        return R.ok();
    }

    @PostMapping(value = "/sessions/{sessionId}/messages:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader("X-User-Id") Long userId,
                             @RequestHeader(value = "X-Agent-Client-Ip", required = false) String clientIp,
                             @PathVariable Long sessionId,
                             @Valid @RequestBody ChatMessageDTO dto) {
        SseEmitter emitter = new SseEmitter(90_000L);
        CompletableFuture.runAsync(() -> {
            if (!agentService.acquireConversationSlot(userId, sessionId, clientIp)) {
                try { emitter.send(SseEmitter.event().name("error").data(Map.of("message", "This conversation is already generating a reply"))); }
                catch (IOException ignored) { }
                emitter.complete();
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("meta").data(Map.of("sessionId", sessionId)));
                emitter.send(SseEmitter.event().name("tool_status").data(Map.of("status", "thinking")));
                AgentReplyVO reply = agentService.streamChat(userId, sessionId, dto, delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(delta));
                    } catch (IOException exception) {
                        throw new IllegalStateException("SSE client disconnected", exception);
                    }
                });
                // Structured UI data stays outside model prose; optional previews cannot fail an answered chat.
                try {
                    String request = dto.getContent() == null ? "" : dto.getContent();
                    boolean excludesShelf = request.contains("不要从书架") || request.contains("不从书架")
                            || request.contains("别从书架") || request.contains("不用书架");
                    emitter.send(SseEmitter.event().name("recommendations").data(excludesShelf
                            ? List.of() : recommendationService.dynamicShelf(userId).stream().limit(3).toList()));
                    if (dto.getCanonicalBookId() != null && dto.getCurrentChapter() != null) {
                        int boundary = spoilerBoundaryService.clamp(userId, dto.getCanonicalBookId(), dto.getCurrentChapter());
                        emitter.send(SseEmitter.event().name("graph").data(knowledgeService.graph(dto.getCanonicalBookId(), boundary)));
                    }
                } catch (Exception previewException) {
                    log.debug("Agent structured SSE preview unavailable for sessionId={}", sessionId);
                }
                emitter.send(SseEmitter.event().name("done").data(reply));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("message", "Agent request failed")));
                } catch (IOException ignored) {
                    // The browser has already closed the stream.
                }
                emitter.completeWithError(e);
            } finally {
                agentService.releaseConversationSlot(sessionId);
            }
        });
        return emitter;
    }

    @GetMapping("/models")
    public R<List<UserModelConfigVO>> listModels(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(agentService.listModelConfigs(userId));
    }

    @PostMapping("/models")
    public R<UserModelConfigVO> saveModel(@RequestHeader("X-User-Id") Long userId,
                                           @Valid @RequestBody SaveModelConfigDTO dto) {
        return R.ok(agentService.saveModelConfig(userId, dto));
    }

    @PostMapping("/models/{configId}:test")
    public R<com.shanyuefang.agent.domain.vo.ModelConnectionTestVO> testModel(@RequestHeader("X-User-Id") Long userId, @PathVariable Long configId) {
        return R.ok(agentService.testModelConfig(userId, configId));
    }

    @PutMapping("/models/{configId}/enabled")
    public R<UserModelConfigVO> setModelEnabled(@RequestHeader("X-User-Id") Long userId, @PathVariable Long configId,
                                                 @RequestParam boolean enabled) {
        return R.ok(agentService.setModelConfigEnabled(userId, configId, enabled));
    }

    @DeleteMapping("/models/{configId}")
    public R<Void> deleteModel(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable Long configId) {
        agentService.deleteModelConfig(userId, configId);
        return R.ok();
    }

    @PostMapping("/quick-recommendations")
    public R<List<Map<String, String>>> quickRecommendations(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(recommendationService.dynamicShelf(userId));
    }

    @GetMapping("/reading-plan")
    public R<ReadingPlanVO> readingPlan(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(recommendationService.readingPlan(userId));
    }

    @GetMapping("/shelf-groups")
    public R<List<Map<String, Object>>> shelfGroups(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(shelfGroupService.groups(userId));
    }

    @PutMapping("/shelf-groups")
    public R<Void> saveShelfGroup(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody SaveShelfGroupDTO dto) {
        shelfGroupService.save(userId, dto);
        return R.ok();
    }

    @PostMapping("/recommendations/feedback")
    public R<Void> recommendationFeedback(@RequestHeader("X-User-Id") Long userId,
                                          @Valid @RequestBody RecommendationFeedbackDTO dto) {
        feedbackService.save(userId, dto);
        return R.ok();
    }

    @GetMapping("/books/{canonicalBookId}/graph")
    public R<KnowledgeGraphVO> graph(@RequestHeader("X-User-Id") long userId, @PathVariable long canonicalBookId,
                                     @RequestParam(defaultValue = "0") int currentChapter,
                                     @RequestParam(defaultValue = "false") boolean spoilersConfirmed) {
        bookKnowledgeBuildService.ensureReadable(userId, canonicalBookId);
        return R.ok(knowledgeService.graph(canonicalBookId, spoilerBoundaryService.clamp(userId, canonicalBookId, currentChapter, spoilersConfirmed)));
    }

    @GetMapping("/books/{canonicalBookId}/knowledge-build:prepare")
    public R<Map<String, Object>> prepareKnowledgeBuild(@RequestHeader("X-User-Id") long userId,
                                                         @PathVariable long canonicalBookId,
                                                         @RequestParam(required = false) Integer startChapter,
                                                         @RequestParam(required = false) Integer endChapter) {
        return R.ok(bookKnowledgeBuildService.prepare(userId, canonicalBookId, startChapter, endChapter));
    }

    @PostMapping("/books/{canonicalBookId}/knowledge-build")
    public R<BookKnowledgeBuildTask> startKnowledgeBuild(@RequestHeader("X-User-Id") long userId,
                                                         @PathVariable long canonicalBookId,
                                                         @Valid @RequestBody StartBookKnowledgeBuildDTO dto) {
        return R.ok(bookKnowledgeBuildService.start(userId, canonicalBookId, dto));
    }

    @GetMapping("/knowledge-build/tasks")
    public R<List<BookKnowledgeBuildTask>> myKnowledgeBuildTasks(@RequestHeader("X-User-Id") long userId,
                                                                  @RequestParam(defaultValue = "30") int limit) {
        return R.ok(bookKnowledgeBuildService.myTasks(userId, limit));
    }

    @DeleteMapping("/knowledge-build/tasks/{taskId}")
    public R<Void> deleteKnowledgeBuildTask(@RequestHeader("X-User-Id") long userId, @PathVariable long taskId) {
        bookKnowledgeBuildService.deleteTask(userId, taskId);
        return R.ok();
    }

    @PutMapping("/books/{canonicalBookId}/knowledge-sharing")
    public R<Void> updateKnowledgeSharing(@RequestHeader("X-User-Id") long userId, @PathVariable long canonicalBookId,
                                          @RequestParam boolean isPublic) {
        bookKnowledgeBuildService.updateSharing(userId, canonicalBookId, isPublic);
        return R.ok();
    }

    @DeleteMapping("/books/{canonicalBookId}/knowledge-graph")
    public R<Void> deleteOwnedKnowledgeGraph(@RequestHeader("X-User-Id") long userId, @PathVariable long canonicalBookId) {
        bookKnowledgeBuildService.deleteOwnedGraph(userId, canonicalBookId);
        return R.ok();
    }

    @GetMapping("/books/knowledge-status")
    public R<Map<Long, Map<String, Object>>> knowledgeStatuses(@RequestParam List<Long> canonicalBookIds) {
        return R.ok(bookKnowledgeBuildService.statuses(canonicalBookIds));
    }

    @GetMapping("/books/{canonicalBookId}/knowledge-status")
    public R<Map<String, Object>> knowledgeStatus(@PathVariable long canonicalBookId) {
        return R.ok(bookKnowledgeBuildService.status(canonicalBookId));
    }

    @GetMapping("/books/{canonicalBookId}/clues")
    public R<List<ClueVO>> clues(@RequestHeader("X-User-Id") long userId, @PathVariable long canonicalBookId,
                                 @RequestParam(defaultValue = "0") int currentChapter,
                                 @RequestParam(defaultValue = "false") boolean spoilersConfirmed) {
        bookKnowledgeBuildService.ensureReadable(userId, canonicalBookId);
        return R.ok(knowledgeService.clues(canonicalBookId, spoilerBoundaryService.clamp(userId, canonicalBookId, currentChapter, spoilersConfirmed)));
    }

    @GetMapping("/books/{canonicalBookId}/timeline")
    public R<List<String>> timeline(@RequestHeader("X-User-Id") long userId, @PathVariable long canonicalBookId,
                                    @RequestParam(defaultValue = "0") int currentChapter,
                                    @RequestParam(defaultValue = "false") boolean spoilersConfirmed) {
        bookKnowledgeBuildService.ensureReadable(userId, canonicalBookId);
        return R.ok(knowledgeService.timeline(canonicalBookId, spoilerBoundaryService.clamp(userId, canonicalBookId, currentChapter, spoilersConfirmed)));
    }

    @GetMapping("/books/{canonicalBookId}/reading-map")
    public R<ReadingMapVO> readingMap(@RequestHeader("X-User-Id") long userId, @PathVariable long canonicalBookId,
                                      @RequestParam(defaultValue = "0") int currentChapter,
                                      @RequestParam(defaultValue = "false") boolean spoilersConfirmed) {
        bookKnowledgeBuildService.ensureReadable(userId, canonicalBookId);
        return R.ok(knowledgeService.readingMap(canonicalBookId, spoilerBoundaryService.clamp(userId, canonicalBookId, currentChapter, spoilersConfirmed)));
    }

    @GetMapping("/books/{canonicalBookId}/capsule")
    public R<PlotCapsuleVO> capsule(@RequestHeader("X-User-Id") long userId, @PathVariable long canonicalBookId,
                                    @RequestParam(defaultValue = "0") int currentChapter,
                                    @RequestParam(defaultValue = "false") boolean spoilersConfirmed) {
        bookKnowledgeBuildService.ensureReadable(userId, canonicalBookId);
        int boundary = spoilerBoundaryService.clamp(userId, canonicalBookId, currentChapter, spoilersConfirmed);
        List<String> allTimeline = knowledgeService.timeline(canonicalBookId, boundary);
        // Show the reader's latest story context rather than the first indexed cards.
        List<String> timeline = allTimeline.subList(Math.max(0, allTimeline.size() - 6), allTimeline.size());
        List<ClueVO> clues = knowledgeService.clues(canonicalBookId, boundary);
        return R.ok(new PlotCapsuleVO(boundary, knowledgeService.recapSummary(canonicalBookId, boundary), timeline, clues,
                spoilersConfirmed ? "你已确认允许剧透；内容会覆盖至所选章节。" : "内容仅来自已建立索引且不超过当前阅读边界的章节，不会引用后续剧情。"));
    }

    @GetMapping("/books/{canonicalBookId}/similar")
    public R<List<SimilarBookVO>> similar(@RequestHeader("X-User-Id") long userId, @PathVariable long canonicalBookId,
                                 @RequestParam(defaultValue = "0") int currentChapter,
                                 @RequestParam(defaultValue = "false") boolean spoilersConfirmed,
                                 @RequestParam(defaultValue = "6") int limit) {
        bookKnowledgeBuildService.ensureReadable(userId, canonicalBookId);
        return R.ok(knowledgeService.similarBooks(canonicalBookId, spoilerBoundaryService.clamp(userId, canonicalBookId, currentChapter, spoilersConfirmed), limit));
    }

    @GetMapping("/books/{canonicalBookId}/reader-link")
    public R<Map<String, Object>> readerLink(@PathVariable long canonicalBookId) {
        R<Map<String, Object>> response = canonicalBookClient.detail(agentProperties.getInternalToken(), canonicalBookId);
        return R.ok(response == null || response.getData() == null ? Map.of() : response.getData());
    }

    @GetMapping("/infrastructure")
    public R<Map<String, Object>> infrastructure(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        long rolloutSubject = userId == null ? 0L : userId;
        String configuredRerankerModel = agentProperties.getRerankerModel();
        String activeRerankerModel;
        try {
            activeRerankerModel = modelRouteService.resolve("RERANKER", rolloutSubject, configuredRerankerModel);
        } catch (Exception exception) {
            // Infrastructure is informational; a route-store outage must not hide service health.
            activeRerankerModel = configuredRerankerModel;
        }
        return R.ok(Map.ofEntries(
                Map.entry("milvusEnabled", agentProperties.isMilvusEnabled()),
                Map.entry("milvusCollection", agentProperties.getMilvusCollection()),
                Map.entry("elasticsearchEnabled", agentProperties.isElasticsearchEnabled()),
                Map.entry("elasticsearchIndex", agentProperties.getElasticsearchIndex()),
                Map.entry("neo4jEnabled", agentProperties.isNeo4jEnabled()),
                Map.entry("graphLlmEnabled", agentProperties.isGraphLlmEnabled()),
                Map.entry("embeddingProvider", agentProperties.getEmbeddingProvider()),
                Map.entry("embeddingModel", agentProperties.getEmbeddingModel()),
                Map.entry("embeddingDimensions", agentProperties.getEmbeddingDimensions()),
                Map.entry("embeddingModelVersion", agentProperties.getEmbeddingModelVersion()),
                Map.entry("retrievalArchitecture", "从问题实体出发的 LightRAG 关系脉络，结合原文章节交叉佐证"),
                Map.entry("rerankerModel", configuredRerankerModel),
                Map.entry("activeRerankerModel", activeRerankerModel),
                Map.entry("rerankerRolloutSubjectPresent", userId != null),
                Map.entry("spoilerBoundary", "canonicalBookId + currentChapter")
        ));
    }

    @GetMapping("/preferences")
    public R<UserAgentPreferenceVO> preferences(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(preferenceService.get(userId));
    }

    @PutMapping("/preferences")
    public R<UserAgentPreferenceVO> savePreferences(@RequestHeader("X-User-Id") Long userId,
                                                    @Valid @RequestBody SaveAgentPreferenceDTO dto) {
        return R.ok(preferenceService.save(userId, dto));
    }

    @DeleteMapping("/preferences/personal-data")
    public R<Void> erasePersonalData(@RequestHeader("X-User-Id") Long userId,
                                     @RequestParam(defaultValue = "true") boolean eraseConversations) {
        preferenceService.erasePersonalData(userId, eraseConversations);
        return R.ok();
    }
}
