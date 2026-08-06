package com.shanyuefang.agent.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.agent.domain.entity.BookKnowledgeBuildTask;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper public interface BookKnowledgeBuildTaskMapper extends BaseMapper<BookKnowledgeBuildTask> {
    @Delete("DELETE FROM t_book_knowledge_build_task WHERE id = #{taskId} AND requester_user_id = #{userId}")
    int deleteOwnedTask(@Param("userId") long userId, @Param("taskId") long taskId);
}
