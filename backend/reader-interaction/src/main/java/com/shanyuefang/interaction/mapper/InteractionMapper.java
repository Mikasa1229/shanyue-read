package com.shanyuefang.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.interaction.domain.entity.Interaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 互动 Mapper
 */
@Mapper
public interface InteractionMapper extends BaseMapper<Interaction> {

    /**
     * 统计某目标的互动数（点赞数 / 收藏数）
     */
    @Select("SELECT COUNT(*) FROM t_interaction " +
            "WHERE target_id = #{targetId} AND target_type = #{targetType} AND action = #{action}")
    int countByTarget(@Param("targetId") Long targetId,
                      @Param("targetType") int targetType,
                      @Param("action") int action);
}
