package com.shanyuefang.user.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserLevelVO {

    private int level;
    private String levelName;
    private long expTotal;
    private long currentLevelExp;
    private long nextLevelExp;
    private long needExpToNext;
    private int progressPercent;
    private List<UserLevelTaskVO> dailyTasks;
}
