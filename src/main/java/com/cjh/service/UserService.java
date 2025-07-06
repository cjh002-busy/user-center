package com.cjh.service;

import com.cjh.model.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author lenovo
* @description 针对表【user】的数据库操作Service
* @createDate 2025-02-02 18:53:57
*/
public interface UserService extends IService<User> {
    /**
     * 用户注册
     * @param account 用户账户
     * @param password 用户密码
     * @param checkPassword 校验密码
     * @param planetCode 星球编号
     * @return 用户id
     */
    long userRegister(String account, String password, String checkPassword, String planetCode);

    /**
     * 用户登录
     * @param account 用户账户
     * @param password 用户密码
     * @return 脱敏后的用户信息
     */
    User userLogin(String account, String password, HttpServletRequest request);

    /**
     * 用户脱敏
     * @param user
     * @return
     */
    User getSafetyUser(User user);

    /**
     * 用户注销
     * @param request
     * @return
     */
    int userLogout(HttpServletRequest request);

    /**
     * 根据标签查询用户
     * @param tagNameList
     * @return
     */
    List<User> searchUserByTags(List<String> tagNameList);
}
