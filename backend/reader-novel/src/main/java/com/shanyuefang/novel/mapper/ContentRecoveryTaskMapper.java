package com.shanyuefang.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.novel.domain.entity.ContentRecoveryTask;
import org.apache.ibatis.annotations.Update;

public interface ContentRecoveryTaskMapper extends BaseMapper<ContentRecoveryTask> {
    @Update("UPDATE t_book_content_recovery_task SET status = 'PROCESSING', started_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{taskId} AND status = 'PENDING'")
    int claim(long taskId);
}
