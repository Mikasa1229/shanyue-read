package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_lightrag_community")
public class LightRagCommunity {
    private Long id; private Long canonicalBookId; private String communityLevel;
    private Integer chapterStart; private Integer chapterEnd; private String summary;
    private String entitySummary; private String communityKey;
    private String contentHash; private String embeddingJson; private String modelVersion;
    private LocalDateTime indexedAt; private LocalDateTime deletedAt;
}
