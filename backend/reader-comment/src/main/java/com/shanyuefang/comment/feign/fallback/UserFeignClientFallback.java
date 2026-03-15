package com.shanyuefang.comment.feign.fallback;

import com.shanyuefang.comment.feign.UserFeignClient;
import com.shanyuefang.comment.feign.vo.UserSimpleVO;
import com.shanyuefang.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 降级处理：用户服务不可用时返回空 Map，不影响点评主流程
 */
@Slf4j
@Component
public class UserFeignClientFallback implements UserFeignClient {

    @Override
    public R<Map<Long, UserSimpleVO>> batchGetUsers(List<Long> ids) {
        log.warn("用户服务 Feign 调用降级，ids={}", ids);
        return R.ok(Collections.emptyMap());
    }
}
