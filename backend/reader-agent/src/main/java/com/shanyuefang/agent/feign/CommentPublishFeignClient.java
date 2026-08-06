package com.shanyuefang.agent.feign;

import com.shanyuefang.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.Map;

/** Publishes a compact, user-authored knowledge-sharing event to the existing square feed. */
@FeignClient(name = "reader-comment", contextId = "agentKnowledgeShareClient", path = "/api/comments")
public interface CommentPublishFeignClient {
    @PostMapping
    R<Object> publish(@RequestHeader("X-User-Id") long userId, @RequestBody Map<String, Object> body);
}
