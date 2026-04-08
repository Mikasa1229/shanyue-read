package com.shanyuefang.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.novel.domain.entity.BookshelfBook;
import com.shanyuefang.novel.domain.vo.HotBookVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookshelfBookMapper extends BaseMapper<BookshelfBook> {

    /** 根据 bookUrl 列表获取各书的代表性元数据 */
    List<BookshelfBook> selectMetaByUrls(@Param("urls") List<String> urls);

    /** 热门书籍：按加入书架用户数排序（DB 兜底） */
    List<HotBookVO> selectHotBooks(@Param("limit") int limit);
}
