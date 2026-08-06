package com.shanyuefang.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.agent.domain.entity.BookKnowledgeChapterCoverage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookKnowledgeChapterCoverageMapper extends BaseMapper<BookKnowledgeChapterCoverage> {
    @Insert("INSERT INTO t_book_knowledge_chapter_coverage (canonical_book_id, chapter_index, completed_at) "
            + "VALUES (#{canonicalBookId}, #{chapterIndex}, #{completedAt}) "
            + "ON CONFLICT (canonical_book_id, chapter_index) DO NOTHING")
    int insertIfAbsent(BookKnowledgeChapterCoverage coverage);
}
