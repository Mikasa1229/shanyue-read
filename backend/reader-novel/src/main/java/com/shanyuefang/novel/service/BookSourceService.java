package com.shanyuefang.novel.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyuefang.novel.domain.entity.BookSource;
import com.shanyuefang.novel.domain.vo.BookChapterVO;
import com.shanyuefang.novel.domain.vo.BookSourceVO;
import com.shanyuefang.novel.domain.vo.SearchBookVO;

import java.util.List;

public interface BookSourceService extends IService<BookSource> {

    /** 从远端 URL 拉取 JSON 并批量导入书源，返回导入数量 */
    int importFromUrl(String url);

    /** 从 JSON 文本直接导入书源（支持单个对象或数组），返回导入数量 */
    int importFromJson(String json);

    /** 分页查询书源列表 */
    Page<BookSourceVO> listSources(int page, int size);

    /** 启用/禁用书源 */
    void toggleEnabled(Long id);

    /** 删除书源 */
    void deleteSource(Long id);

    /** 使用指定书源搜索书籍 */
    List<SearchBookVO> search(Long sourceId, String keyword, int page);

    /** 获取书籍目录（章节列表） */
    List<BookChapterVO> getChapters(Long sourceId, String bookUrl);

    /** 获取章节正文内容 */
    String getContent(Long sourceId, String chapterUrl);
}
