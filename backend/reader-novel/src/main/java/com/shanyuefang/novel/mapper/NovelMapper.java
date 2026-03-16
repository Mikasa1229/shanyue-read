package com.shanyuefang.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.novel.domain.entity.Novel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NovelMapper extends BaseMapper<Novel> {

    @Update("UPDATE t_novel SET view_count = view_count + 1 WHERE id = #{id} AND deleted = FALSE")
    void incrementViewCount(@Param("id") Long id);

    @Update("UPDATE t_novel SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{id} AND deleted = FALSE")
    void updateLikeCount(@Param("id") Long id, @Param("delta") int delta);

    @Update("UPDATE t_novel SET favorite_count = GREATEST(0, favorite_count + #{delta}) WHERE id = #{id} AND deleted = FALSE")
    void updateFavoriteCount(@Param("id") Long id, @Param("delta") int delta);
}
