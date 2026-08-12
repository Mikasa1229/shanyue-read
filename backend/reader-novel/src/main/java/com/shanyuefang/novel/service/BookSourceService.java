package com.shanyuefang.novel.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyuefang.novel.domain.entity.BookSource;
import com.shanyuefang.novel.domain.vo.BookChapterVO;
import com.shanyuefang.novel.domain.vo.BookSourceVO;
import com.shanyuefang.novel.domain.vo.SearchBookVO;
import com.shanyuefang.novel.domain.vo.AggregatedBookVO;

import java.util.List;
import java.util.Map;

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

    /** 获取书源书籍详情（封面、作者、简介、分类等） */
    SearchBookVO getBookDetail(Long sourceId, String bookUrl);

    /** 聚合搜索：并发调用所有启用书源，合并结果 */
    List<SearchBookVO> aggregateSearch(String keyword, int page);

    /** One canonical work per result, retaining every discovered readable source mirror. */
    List<AggregatedBookVO> aggregateCanonicalSearch(String keyword, int page);

    /** All known source mirrors for a canonical work, ordered by current preference. */
    List<AggregatedBookVO> canonicalBookSources(Long canonicalBookId);

    /** 获取书籍目录（章节列表） */
    List<BookChapterVO> getChapters(Long sourceId, String bookUrl);

    /** 获取分页章节目录（默认每次 50 章） */
    Map<String, Object> getChaptersPage(Long sourceId, String bookUrl, int offset, int limit);

    /** 获取章节正文内容 */
    String getContent(Long sourceId, String chapterUrl);

    /** 测试书源是否可访问，返回 accessible / statusCode / responseMs */
    Map<String, Object> testSource(Long sourceId);

    /** 调试：返回章节提取中间结果 */
    Map<String, Object> debugChapters(Long sourceId, String bookUrl);
}
