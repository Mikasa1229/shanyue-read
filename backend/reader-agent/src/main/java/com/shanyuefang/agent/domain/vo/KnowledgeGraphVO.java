package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class KnowledgeGraphVO {
    private List<Node> nodes;
    private List<Edge> edges;

    @Data @AllArgsConstructor
    public static class Node {
        private Long id;
        private String name;
        private String type;
        private Integer firstChapter;
        private String evidence;
        private Double confidence;
    }

    @Data @AllArgsConstructor
    public static class Edge {
        private Long source;
        private Long target;
        private String relation;
        private Integer firstChapter;
        private String evidence;
        private Double confidence;
    }
}
