package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessageDTO {
    @NotBlank
    @Size(max = 4000)
    private String content;
    @Size(max = 32)
    private String mode = "PLATFORM";
    private Long modelConfigId;
    private Long canonicalBookId;
    private Integer currentChapter;
    @Size(max = 512)
    private String currentBookTitle;
    @Size(max = 64)
    private String interviewCharacter;
    private Boolean reuseExistingUserMessage = false;
}
