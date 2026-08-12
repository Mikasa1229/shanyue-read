package com.shanyuefang.agent.controller;

import com.shanyuefang.agent.service.AgentInternalAccess;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Token-protected evaluation surface; never exposed through the browser gateway. */
@RestController
@RequestMapping("/internal/agent/evaluation")
@RequiredArgsConstructor
public class InternalEvaluationController {
    private final AgentInternalAccess internalAccess;
    private final KnowledgeService knowledgeService;

    @GetMapping("/books/{canonicalBookId}/retrieve")
    public R<Map<String, Object>> retrieve(@RequestHeader("X-Agent-Internal-Token") String token,
                                           @PathVariable long canonicalBookId,
                                           @RequestParam int currentChapter,
                                           @RequestParam String query,
                                           @RequestParam(defaultValue = "5") int limit) {
        internalAccess.require(token);
        KnowledgeService.RetrievalResult result = knowledgeService.retrieveDetailed(canonicalBookId, currentChapter, query, limit, 0L);
        return R.ok(Map.of("evidence", result.evidence(), "candidateCount", result.candidateCount(),
                "selectedCount", result.selectedCount(), "sourceCandidateCounts", result.sourceCandidateCounts()));
    }
}
