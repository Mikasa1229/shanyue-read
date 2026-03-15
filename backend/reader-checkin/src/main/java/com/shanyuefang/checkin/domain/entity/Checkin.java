package com.shanyuefang.checkin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_checkin")
public class Checkin {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;
    private Long novelId;
    private LocalDate checkinDate;
    private String note;
    private LocalDateTime createdAt;
}
