package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Spoiler-bounded event map with a main/side narrative classification and evidence. */
@Data
@AllArgsConstructor
public class ReadingMapVO {
    private List<Event> events;
    private List<Link> links;

    @Data
    @AllArgsConstructor
    public static class Event {
        private Long id;
        private String name;
        private String branch;
        private Integer chapterIndex;
        private String evidence;
        private Double confidence;
    }

    @Data
    @AllArgsConstructor
    public static class Link {
        private Long source;
        private Long target;
        private String relation;
        private String evidence;
        private Double confidence;
    }
}
