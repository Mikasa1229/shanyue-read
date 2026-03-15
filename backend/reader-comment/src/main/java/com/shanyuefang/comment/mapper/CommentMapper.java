package com.shanyuefang.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.comment.domain.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 点评 Mapper
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 批量软删除某根评论下的所有回复
     *
     * @param rootId 根评论 ID
     * @return 影响行数
     */
    int softDeleteRepliesByRootId(@Param("rootId") Long rootId);
}
