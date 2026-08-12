package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** A deterministic plan based only on the user's shelf projection. */
@Data
@AllArgsConstructor
public class ReadingPlanVO {
    private int dailyTargetChapters;
    private int activeBookCount;
    private String summary;
    private List<Item> items;

    @Data
    @AllArgsConstructor
    public static class Item {
        private Long canonicalBookId;
        private String title;
        private int currentChapter;
        private Integer totalChapters;
        private int suggestedChaptersToday;
        private String reason;
    }
}
