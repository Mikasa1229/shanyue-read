package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Safe diagnostic returned after a real OpenAI-compatible chat completion probe. */
@Data
@AllArgsConstructor
public class ModelConnectionTestVO {
    private String model;
    private String baseUrl;
    private long latencyMs;
    private String responsePreview;
}
