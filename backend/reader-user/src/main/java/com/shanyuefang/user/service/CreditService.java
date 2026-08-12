package com.shanyuefang.user.service;

import com.shanyuefang.user.domain.dto.CreditOperationDTO;
import com.shanyuefang.user.domain.vo.UserCreditVO;

public interface CreditService {
    void grantStarterCredits(long userId);
    UserCreditVO getCredits(long userId);
    void grant(CreditOperationDTO dto);
    void freeze(CreditOperationDTO dto);
    void settle(CreditOperationDTO dto);
    void refund(CreditOperationDTO dto);
}
