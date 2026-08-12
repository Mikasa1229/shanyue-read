package com.shanyuefang.user.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_credit_account")
public class UserCreditAccount {
    @TableId
    private Long userId;
    private Integer availableCredits;
    private Integer frozenCredits;
    private Long version;
    private LocalDateTime updatedAt;
}
