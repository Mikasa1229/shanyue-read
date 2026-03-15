package com.shanyuefang.interaction.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 点赞/收藏记录实体
 */
@Data
@TableName("t_interaction")
public class Interaction {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long targetId;

    /** 目标类型：1=小说 2=点评 */
    private Integer targetType;

    /** 行为：1=点赞 2=收藏 */
    private Integer action;

    private LocalDateTime createdAt;
}
