package com.shanyuefang.agent.controller;

import com.shanyuefang.agent.domain.dto.IndexChapterDTO;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.agent.service.BookKnowledgeBuildService;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.service.StructuredGraphExtractor;
import com.shanyuefang.agent.service.AgentInternalAccess;
import com.shanyuefang.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Called by the novel service after it has obtained content from an authorized book source. */
@RestController
@RequestMapping("/internal/agent/knowledge")
@RequiredArgsConstructor
public class InternalKnowledgeController {
    private final KnowledgeService knowledgeService;
    private final BookKnowledgeBuildService bookKnowledgeBuildService;
    private final AgentInternalAccess internalAccess;
    private final AgentProperties agentProperties;

    @PostMapping("/chapters")
    public R<Void> index(@RequestHeader("X-Agent-Internal-Token") String token, @Valid @RequestBody IndexChapterDTO dto) {
        internalAccess.require(token);
        knowledgeService.indexChapter(dto);
        return R.ok();
    }

    @PostMapping("/books/{canonicalBookId}/graph:rebuild")
    public R<Void> rebuildGraph(@RequestHeader("X-Agent-Internal-Token") String token, @org.springframework.web.bind.annotation.PathVariable long canonicalBookId) {
        internalAccess.require(token);
        knowledgeService.rebuildGraph(canonicalBookId);
        bookKnowledgeBuildService.synchronizeAllIndexedChapters(canonicalBookId, true);
        return R.ok();
    }

    /** Bounded operational rebuild used to validate an extractor against a known chapter window. */
    @PostMapping("/books/{canonicalBookId}/graph:rebuild-range")
    public R<Void> rebuildGraphRange(@RequestHeader("X-Agent-Internal-Token") String token,
                                     @org.springframework.web.bind.annotation.PathVariable long canonicalBookId,
                                     @org.springframework.web.bind.annotation.RequestParam int startChapter,
                                     @org.springframework.web.bind.annotation.RequestParam int endChapter,
                                     @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean replaceGraph) {
        internalAccess.require(token);
        // Quality iterations must not upsert over stale claims from the previous extractor version.
        // Raw documents and chunks are intentionally retained by clearGraph.
        StructuredGraphExtractor.ModelConfig config = new StructuredGraphExtractor.ModelConfig(agentProperties.getPlatformProvider(),
                agentProperties.getPlatformModel(), agentProperties.getPlatformBaseUrl(), agentProperties.getPlatformApiKey());
        if (replaceGraph) knowledgeService.replaceGraphRange(canonicalBookId, startChapter, endChapter, config, ignored -> { });
        else knowledgeService.buildGraphRange(canonicalBookId, startChapter, endChapter, config, ignored -> { });
        // Operational rebuilds bypass the user task worker, so they must publish the same durable
        // coverage metadata used by the insight UI after the graph transaction succeeds.
        bookKnowledgeBuildService.synchronizeCompletedRange(canonicalBookId, startChapter, endChapter, replaceGraph);
        return R.ok();
    }

    @PostMapping("/books/{canonicalBookId}/insights:rebuild-range")
    public R<Void> rebuildInsights(@RequestHeader("X-Agent-Internal-Token") String token,
                                   @org.springframework.web.bind.annotation.PathVariable long canonicalBookId,
                                   @org.springframework.web.bind.annotation.RequestParam int startChapter,
                                   @org.springframework.web.bind.annotation.RequestParam int endChapter) {
        internalAccess.require(token);
        knowledgeService.rebuildDerivedInsights(canonicalBookId, startChapter, endChapter,
                new StructuredGraphExtractor.ModelConfig(agentProperties.getPlatformProvider(), agentProperties.getPlatformModel(),
                        agentProperties.getPlatformBaseUrl(), agentProperties.getPlatformApiKey()));
        return R.ok();
    }

    @PostMapping("/books/{canonicalBookId}/characters:rebuild-range")
    public R<Void> rebuildCharacters(@RequestHeader("X-Agent-Internal-Token") String token,
                                     @org.springframework.web.bind.annotation.PathVariable long canonicalBookId,
                                     @org.springframework.web.bind.annotation.RequestParam int startChapter,
                                     @org.springframework.web.bind.annotation.RequestParam int endChapter) {
        internalAccess.require(token);
        knowledgeService.rebuildCharacterKnowledge(canonicalBookId, startChapter, endChapter,
                new StructuredGraphExtractor.ModelConfig(agentProperties.getPlatformProvider(), agentProperties.getPlatformModel(),
                        agentProperties.getPlatformBaseUrl(), agentProperties.getPlatformApiKey()));
        return R.ok();
    }

    @PostMapping("/books/{canonicalBookId}/evidence:reproject")
    public R<Void> reprojectEvidence(@RequestHeader("X-Agent-Internal-Token") String token,
                                     @org.springframework.web.bind.annotation.PathVariable long canonicalBookId,
                                     @org.springframework.web.bind.annotation.RequestParam(defaultValue = "100") int maxChunks) {
        internalAccess.require(token);
        knowledgeService.reprojectEvidence(canonicalBookId, maxChunks);
        return R.ok();
    }

    @PostMapping("/books/{canonicalBookId}/graph:reproject")
    public R<Void> reprojectGraph(@RequestHeader("X-Agent-Internal-Token") String token,
                                  @org.springframework.web.bind.annotation.PathVariable long canonicalBookId) {
        internalAccess.require(token);
        knowledgeService.reprojectGraph(canonicalBookId);
        return R.ok();
    }
}
