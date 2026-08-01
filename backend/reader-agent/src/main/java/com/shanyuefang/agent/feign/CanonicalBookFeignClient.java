package com.shanyuefang.agent.feign;

import com.shanyuefang.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.Map;
import java.util.List;

@FeignClient(name = "reader-novel", contextId = "agentCanonicalBookClient", path = "/internal/books/canonical")
public interface CanonicalBookFeignClient {
    @GetMapping("/{canonicalBookId}")
    R<Map<String, Object>> detail(@RequestHeader("X-Agent-Internal-Token") String token, @PathVariable("canonicalBookId") long canonicalBookId);
    @GetMapping("/search")
    R<List<Map<String, Object>>> search(@RequestHeader("X-Agent-Internal-Token") String token, @org.springframework.web.bind.annotation.RequestParam("keyword") String keyword,
                                         @org.springframework.web.bind.annotation.RequestParam("limit") int limit);
}
