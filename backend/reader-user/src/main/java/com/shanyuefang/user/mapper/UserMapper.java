package com.shanyuefang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.user.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 * MyBatis-Plus 提供标准 CRUD，自定义查询写在 UserMapper.xml
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
