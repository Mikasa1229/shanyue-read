package com.shanyuefang.agent.controller;

import com.shanyuefang.agent.domain.dto.IndexChapterDTO;
import com.shanyuefang.agent.service.KnowledgeService;
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
    private final AgentInternalAccess internalAccess;

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
