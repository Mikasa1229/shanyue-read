package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveModelConfigDTO {
    /**
     * Deprecated compatibility field. All user models use the OpenAI-compatible
     * Chat Completions protocol regardless of the vendor behind the endpoint.
     */
    @Size(max = 64)
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
