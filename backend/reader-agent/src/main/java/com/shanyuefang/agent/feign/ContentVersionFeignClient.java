package com.shanyuefang.agent.feign;

import com.shanyuefang.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "reader-novel", contextId = "agentContentVersionClient", path = "/internal/books/content-versions")
public interface ContentVersionFeignClient {
    @PutMapping("/status")
    R<Void> updateStatus(@RequestHeader("X-Agent-Internal-Token") String token, @RequestBody Map<String, Object> payload);
}
