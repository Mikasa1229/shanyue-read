package com.shanyuefang.agent.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.agent.domain.entity.BookKnowledgeBuildTask;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
@Mapper public interface BookKnowledgeBuildTaskMapper extends BaseMapper<BookKnowledgeBuildTask> {
    @Delete("DELETE FROM t_book_knowledge_build_task WHERE id = #{taskId} AND requester_user_id = #{userId}")
    int deleteOwnedTask(@Param("userId") long userId, @Param("taskId") long taskId);

    /** Atomically claims an at-least-once queue message so duplicate deliveries do not repeat a build. */
    @Update("UPDATE t_book_knowledge_build_task SET status = 'RUNNING', started_at = COALESCE(started_at, NOW()), " +
            "message = '正在分析章节并提取实体关系', updated_at = NOW() WHERE id = #{taskId} AND status = 'QUEUED'")
    int claimQueuedTask(@Param("taskId") long taskId);
}
