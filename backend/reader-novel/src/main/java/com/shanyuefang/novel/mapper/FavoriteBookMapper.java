package com.shanyuefang.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.novel.domain.entity.FavoriteBook;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteBookMapper extends BaseMapper<FavoriteBook> {
}
