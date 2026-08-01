package com.shanyuefang.agent.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserAgentPreferenceVO {
    private List<String> preferredGenres;
    private List<String> avoidedThemes;
    private String spoilerLevel;
    private Boolean personalizationEnabled;
    private Boolean retainConversations;
}
