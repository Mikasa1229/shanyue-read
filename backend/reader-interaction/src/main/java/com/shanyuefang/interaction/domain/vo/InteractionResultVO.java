package com.shanyuefang.interaction.domain.vo;

import lombok.AllArgsConstructor;
<parameter name="content">package com.shanyuefang.interaction.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 点赞 / 收藏操作结果 VO
 */
@Data
@AllArgsConstructor
public class InteractionResultVO {

    /** 操作后是否处于已点赞/已收藏状态 */
    private Boolean active;

    /** 操作后目标最新计数 */
    private Integer count;
}
