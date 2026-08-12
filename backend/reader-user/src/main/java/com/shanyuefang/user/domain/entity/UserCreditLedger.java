package com.shanyuefang.user.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_credit_ledger")
public class UserCreditLedger {
    @TableId
    private Long id;
    private Long userId;
    private String operation;
    private Integer amount;
    private String requestId;
    private String reason;
    private LocalDateTime createdAt;
}
