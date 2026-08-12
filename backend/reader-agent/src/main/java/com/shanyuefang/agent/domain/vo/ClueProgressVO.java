package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** A spoiler-bounded milestone in a clue's lifecycle. */
@Data
@AllArgsConstructor
public class ClueProgressVO {
    private Integer chapterIndex;
    private String type;
    private String evidence;
    private String explanation;
}
