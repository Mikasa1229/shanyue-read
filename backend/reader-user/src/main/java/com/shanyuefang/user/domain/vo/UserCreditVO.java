package com.shanyuefang.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserCreditVO {
    private int availableCredits;
    private int frozenCredits;
}
