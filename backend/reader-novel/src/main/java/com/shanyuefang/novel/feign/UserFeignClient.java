package com.shanyuefang.novel.feign;

import com.shanyuefang.common.result.R;
import com.shanyuefang.novel.feign.vo.UserSimpleVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 客户端（调用内部接口，不经 Gateway）
 */
@FeignClient(
        name = "reader-user",
        path = "/internal/users"
)
public interface UserFeignClient {

    /**
     * 批量获取用户简要信息
     *
     * @param ids 用户 ID 列表
     * @return Map<userId, UserSimpleVO>
     */
    @GetMapping("/batch")
    R<Map<Long, UserSimpleVO>> batchGetUsers(@RequestParam("ids") List<Long> ids);
}
