package com.shanyuefang.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.checkin.domain.entity.Checkin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CheckinMapper extends BaseMapper<Checkin> {

    /** 查询用户累计打卡总天数（去重按日期） */
    @Select("SELECT COUNT(DISTINCT checkin_date) FROM t_checkin WHERE user_id = #{userId}")
    int countTotalDays(@Param("userId") Long userId);
}
