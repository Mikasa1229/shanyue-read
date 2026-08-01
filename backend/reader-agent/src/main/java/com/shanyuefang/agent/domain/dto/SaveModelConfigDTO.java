package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveModelConfigDTO {
    @NotBlank
    @Pattern(regexp = "(?i)deepseek|openai", message = "Only configured providers are supported")
    private String provider;
    @NotBlank
    @Size(max = 128)
    private String model;
    @NotBlank
    @Size(min = 8, max = 512)
    private String apiKey;
    @Size(max = 512)
    private String baseUrl;
}
