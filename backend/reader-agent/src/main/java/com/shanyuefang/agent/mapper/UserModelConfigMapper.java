package com.shanyuefang.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.agent.domain.entity.UserModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserModelConfigMapper extends BaseMapper<UserModelConfig> {
    /**
     * Remove the credential record physically.  Model keys are secrets and a deleted
     * configuration must not be restorable through a later page refresh.
     */
    @Delete("DELETE FROM t_user_model_config WHERE id = #{configId} AND user_id = #{userId} AND deleted = FALSE")
    int deleteOwnedConfig(@Param("userId") long userId, @Param("configId") long configId);
}
