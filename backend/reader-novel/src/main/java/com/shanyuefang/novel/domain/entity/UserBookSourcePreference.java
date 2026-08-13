package com.shanyuefang.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Per-user source visibility. Shared BookSource.enabled remains an operational catalog flag. */
@Data
@TableName("t_user_book_source_preference")
public class UserBookSourcePreference {
    private Long userId;
    private Long sourceId;
    private Boolean disabled;
    private LocalDateTime updatedAt;
}
