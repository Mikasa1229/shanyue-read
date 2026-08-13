package com.shanyuefang.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyuefang.novel.domain.entity.BookSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BookSourceMapper extends BaseMapper<BookSource> {

    /**
     * Keep sources a reader can use ahead of their personal disabled sources before pagination.
     * Applying this after a normal page query would still let disabled sources consume early pages.
     */
    @Select("""
            SELECT s.*
            FROM t_book_source s
            LEFT JOIN t_user_book_source_preference p
              ON p.source_id = s.id
             AND p.user_id = #{userId}
             AND p.disabled = TRUE
            ORDER BY CASE WHEN s.enabled = TRUE AND p.source_id IS NULL THEN 0 ELSE 1 END,
                     s.created_at DESC
            """)
    Page<BookSource> selectPageForUser(Page<BookSource> page, @Param("userId") long userId);
}
