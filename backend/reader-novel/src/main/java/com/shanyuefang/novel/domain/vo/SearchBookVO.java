package com.shanyuefang.novel.domain.vo;

import lombok.Data;

/** 通过书源搜索到的书籍信息 */
@Data
public class SearchBookVO {
    /** 书名 */
    private String name;
    /** 作者 */
    private String author;
    /** 封面图 URL */
    private String coverUrl;
    /** 简介 */
    private String intro;
    /** 分类/标签 */
    private String kind;
    /** 最新章节 */
    private String lastChapter;
    /** 字数 */
    private String wordCount;
    /**
     * 书籍详情页 URL（后续用于获取目录/内容，需传回来）
     * 注意：这是原始书源网站的 URL，非本系统 URL
     */
    private String bookUrl;
    /** 来源书源 ID */
    private Long sourceId;
    /** 来源书源名称 */
    private String sourceName;
}
