package com.shanyuefang.novel.domain.vo;

import lombok.Data;

/** 书籍章节信息 */
@Data
public class BookChapterVO {
    /** 章节序号（从 0 开始） */
    private Integer index;
    /** 章节名 */
    private String chapterName;
    /** 章节 URL（原书源网站 URL） */
    private String chapterUrl;
}
