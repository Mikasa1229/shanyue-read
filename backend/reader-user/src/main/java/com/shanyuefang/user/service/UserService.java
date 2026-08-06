package com.shanyuefang.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyuefang.user.domain.dto.LoginDTO;
import com.shanyuefang.user.domain.dto.RegisterDTO;
import com.shanyuefang.user.domain.dto.LevelActionDTO;
import com.shanyuefang.user.domain.dto.UpdatePasswordDTO;
import com.shanyuefang.user.domain.dto.UpdateUserDTO;
import com.shanyuefang.user.domain.entity.User;
import com.shanyuefang.user.domain.vo.LevelActionResultVO;
import com.shanyuefang.user.domain.vo.LoginVO;
import com.shanyuefang.user.domain.vo.UserLevelVO;
import com.shanyuefang.user.domain.vo.UserVO;

public interface UserService extends IService<User> {

    /** 注册 */
    void register(RegisterDTO dto);

    /** 登录，返回 Token + 用户信息 */
    LoginVO login(LoginDTO dto);

    /** 登出 */
    void logout();

    /** 获取当前登录用户信息 */
    UserVO getCurrentUser(Long userId);

    /** 更新用户基本信息（昵称、头像） */
    UserVO updateUser(Long userId, UpdateUserDTO dto);

    /** 修改密码 */
    void updatePassword(Long userId, UpdatePasswordDTO dto);

    /** 获取当前用户等级信息（等级 + 今日任务） */
    UserLevelVO getUserLevel(Long userId);

    /** 记录用户行为并尝试发放任务经验 */
    LevelActionResultVO recordLevelAction(Long userId, LevelActionDTO dto);

    /** Receives a bounded reading increment from the server-side reading session only. */
    LevelActionResultVO recordVerifiedReading(Long userId, int seconds);
}
