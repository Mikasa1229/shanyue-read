package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** A deterministic, reading-boundary-safe recap payload for the compact Agent UI. */
@Data
@AllArgsConstructor
public class PlotCapsuleVO {
    private int throughChapter;
    private String summary;
    private List<String> timeline;
    private List<ClueVO> unresolvedClues;
    private String safetyNote;
}
