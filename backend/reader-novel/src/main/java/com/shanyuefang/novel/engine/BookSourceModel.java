package com.shanyuefang.novel.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * legado 书源 JSON 的 Java 映射模型（只取本引擎关心的字段）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookSourceModel {

    /** 书源名称 */
    @JsonProperty("bookSourceName")
    private String bookSourceName;

    /** 书源 base URL */
    @JsonProperty("bookSourceUrl")
    private String bookSourceUrl;

    /** 0=小说, 1=有声, 2=图片 */
    @JsonProperty("bookSourceType")
    private Integer bookSourceType;

    /** 分组 */
    @JsonProperty("bookSourceGroup")
    private String bookSourceGroup;

    /** 是否启用 */
    @JsonProperty("enabled")
    private Boolean enabled;

    /** 全局请求头 JSON 字符串 */
    @JsonProperty("header")
    private String header;

    /** 书源编码（如 GBK、UTF-8，默认 UTF-8） */
    @JsonProperty("bookSourceCharset")
    private String bookSourceCharset;

    // ─── 搜索规则（扁平字段，legado 3.x）─────────────────────

    @JsonProperty("searchUrl")
    private String searchUrl;

    @JsonProperty("searchList")
    private String searchList;

    @JsonProperty("searchName")
    private String searchName;

    @JsonProperty("searchAuthor")
    private String searchAuthor;

    @JsonProperty("searchCover")
    private String searchCover;

    @JsonProperty("searchIntro")
    private String searchIntro;

    @JsonProperty("searchKind")
    private String searchKind;

    @JsonProperty("searchLastChapter")
    private String searchLastChapter;

    @JsonProperty("searchWordCount")
    private String searchWordCount;

    @JsonProperty("searchBookUrl")
    private String searchBookUrl;

    // ─── 搜索规则（嵌套对象，legado 2.x 兼容）───────────────

    @JsonProperty("ruleSearch")
    private SearchRule ruleSearch;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchRule {
        @JsonProperty("bookList")    private String bookList;
        @JsonProperty("name")        private String name;
        @JsonProperty("author")      private String author;
        @JsonProperty("coverUrl")    private String coverUrl;
        @JsonProperty("intro")       private String intro;
        @JsonProperty("kind")        private String kind;
        @JsonProperty("lastChapter") private String lastChapter;
        @JsonProperty("wordCount")   private String wordCount;
        @JsonProperty("bookUrl")     private String bookUrl;
    }

    /** 获取有效的 searchList 规则（优先扁平字段，退而使用嵌套 ruleSearch） */
    public String effectiveSearchList() {
        if (searchList != null && !searchList.isBlank()) return searchList;
        return ruleSearch != null ? ruleSearch.getBookList() : null;
    }

    public String effectiveSearchName()       { return firstNonBlank(searchName,        ruleSearch != null ? ruleSearch.getName() : null); }
    public String effectiveSearchAuthor()     { return firstNonBlank(searchAuthor,       ruleSearch != null ? ruleSearch.getAuthor() : null); }
    public String effectiveSearchCover()      { return firstNonBlank(searchCover,        ruleSearch != null ? ruleSearch.getCoverUrl() : null); }
    public String effectiveSearchIntro()      { return firstNonBlank(searchIntro,        ruleSearch != null ? ruleSearch.getIntro() : null); }
    public String effectiveSearchKind()       { return firstNonBlank(searchKind,         ruleSearch != null ? ruleSearch.getKind() : null); }
    public String effectiveSearchLastChapter(){ return firstNonBlank(searchLastChapter,  ruleSearch != null ? ruleSearch.getLastChapter() : null); }
    public String effectiveSearchBookUrl()    { return firstNonBlank(searchBookUrl,      ruleSearch != null ? ruleSearch.getBookUrl() : null); }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }

    // ─── 书籍详情规则 ─────────────────────────────────────────

    @JsonProperty("ruleBookInfo")
    private BookInfoRule ruleBookInfo;

    // ─── 目录规则 ────────────────────────────────────────────

    @JsonProperty("ruleToc")
    private TocRule ruleToc;

    // ─── 内容规则 ────────────────────────────────────────────

    @JsonProperty("ruleContent")
    private ContentRule ruleContent;

    // ─── 内嵌规则类 ──────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BookInfoRule {
        @JsonProperty("name")        private String name;
        @JsonProperty("author")      private String author;
        @JsonProperty("coverUrl")    private String coverUrl;
        @JsonProperty("intro")       private String intro;
        @JsonProperty("kind")        private String kind;
        @JsonProperty("lastChapter") private String lastChapter;
        @JsonProperty("wordCount")   private String wordCount;
        @JsonProperty("tocUrl")      private String tocUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TocRule {
        @JsonProperty("chapterList")  private String chapterList;
        @JsonProperty("chapterName")  private String chapterName;
        @JsonProperty("chapterUrl")   private String chapterUrl;
        @JsonProperty("nextTocUrl")   private String nextTocUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentRule {
        @JsonProperty("content")        private String content;
        @JsonProperty("nextContentUrl") private String nextContentUrl;
    }
}
