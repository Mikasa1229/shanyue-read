package com.shanyuefang.interaction.feign;

import com.shanyuefang.common.result.R;
import com.shanyuefang.interaction.feign.vo.NovelSimpleVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 小说服务 Feign 客户端（调用内部接口，不经 Gateway）
 */
@FeignClient(
        name = "reader-novel",
        path = "/internal/novels"
)
public interface NovelFeignClient {

    /**
     * 批量获取小说简要信息
     *
     * @param ids 小说 ID 列表
     * @return Map<novelId, NovelSimpleVO>
     */
    @GetMapping("/batch")
    R<Map<Long, NovelSimpleVO>> batchGetNovels(@RequestParam("ids") List<Long> ids);
}
