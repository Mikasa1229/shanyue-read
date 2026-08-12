package com.shanyuefang.novel.domain.vo;

import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 热门书籍排行榜条目（按加入书架的用户数排序）
 */
@Data
public class HotBookVO {

    /** 规范作品 ID；同一作品的不同书源共用一个排行条目。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long canonicalBookId;

    /** 名次（从 1 开始） */
    private int rank;

    /** 书名 */
    private String bookName;

    /** 作者 */
    private String author;

    /** 封面图 URL */
    private String coverUrl;

    /** 书籍唯一标识（书源中的 URL） */
    private String bookUrl;

    /** 书源 ID */
    private Long sourceId;

    /** 书源名称 */
    private String sourceName;

    /** 加入书架的用户数 */
    private long shelfCount;
}
