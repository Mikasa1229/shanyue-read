package com.shanyuefang.agent.feign;

import com.shanyuefang.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "reader-user", contextId = "agentUserCreditClient", path = "/internal/users/credits")
public interface UserCreditFeignClient {
    @PostMapping("/freeze")
    R<Void> freeze(@RequestBody CreditOperationRequest request);

    @PostMapping("/settle")
    R<Void> settle(@RequestBody CreditOperationRequest request);

    @PostMapping("/refund")
    R<Void> refund(@RequestBody CreditOperationRequest request);
}
