package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SaveAgentPreferenceDTO {
    @Size(max = 12)
    private List<@Size(max = 32) String> preferredGenres = List.of();
    @Size(max = 12)
    private List<@Size(max = 32) String> avoidedThemes = List.of();
    @Pattern(regexp = "STRICT|STANDARD")
    private String spoilerLevel = "STRICT";
    private Boolean personalizationEnabled = true;
    private Boolean retainConversations = true;
}
