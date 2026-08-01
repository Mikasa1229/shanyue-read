package com.shanyuefang.agent.feign;

import com.shanyuefang.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

@FeignClient(name = "reader-novel", contextId = "agentNovelShelfClient", path = "/internal/bookshelves")
public interface NovelShelfFeignClient {
    @GetMapping
    R<List<Map<String, Object>>> list(@RequestHeader("X-Agent-Internal-Token") String token, @RequestParam("userId") long userId);
    @GetMapping("/hot")
    R<List<Map<String, Object>>> hot(@RequestHeader("X-Agent-Internal-Token") String token, @RequestParam("limit") int limit);
    @GetMapping("/favorites")
    R<List<Long>> favorites(@RequestHeader("X-Agent-Internal-Token") String token, @RequestParam("userId") long userId);
    @GetMapping("/reading-boundary")
    R<Map<String, Integer>> readingBoundary(@RequestHeader("X-Agent-Internal-Token") String token, @RequestParam("userId") long userId, @RequestParam("canonicalBookId") long canonicalBookId);
}
