package com.shanyuefang.agent.feign;

import com.shanyuefang.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/** Requests source-side refetching through its durable recovery queue. */
@FeignClient(name = "reader-novel", contextId = "agentContentRecoveryClient", path = "/internal/books/content-versions")
public interface ContentRecoveryFeignClient {
    @PostMapping("/recovery/{canonicalBookId}")
    R<Map<String, Object>> recover(@RequestHeader("X-Agent-Internal-Token") String token,
                                   @PathVariable long canonicalBookId,
                                   @RequestParam int startChapter,
                                   @RequestParam int endChapter);

    @PostMapping("/prefetch/{canonicalBookId}")
    R<Map<String, Object>> prefetch(@RequestHeader("X-Agent-Internal-Token") String token,
                                    @PathVariable long canonicalBookId,
                                    @RequestParam long userId,
                                    @RequestParam int startChapter,
                                    @RequestParam int endChapter);

    @org.springframework.web.bind.annotation.GetMapping("/prefetch/tasks/{taskId}")
    R<Map<String, Object>> prefetchTask(@RequestHeader("X-Agent-Internal-Token") String token,
                                        @PathVariable long taskId, @RequestParam long userId);
}
