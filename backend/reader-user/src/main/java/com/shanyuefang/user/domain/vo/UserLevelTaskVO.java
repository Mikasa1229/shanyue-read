package com.shanyuefang.user.domain.vo;

import lombok.Data;

@Data
public class UserLevelTaskVO {

    private String taskId;
    private String title;
    private String description;
    private int target;
    private int progress;
    private int rewardExp;
    private boolean completed;
}
