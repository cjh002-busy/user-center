package com.cjh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjh.common.ErrorCode;
import com.cjh.exception.BusinessException;
import com.cjh.model.domain.User;
import com.cjh.service.UserService;
import com.cjh.mapper.UserMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.cjh.constant.UserConstant.USER_LOGIN_STATUS;

/**
* @author lenovo
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-02-02 18:53:57
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Autowired
    UserMapper userMapper;
    private static final String SALT = "cjh";

    @Override
    public long userRegister(String account, String password, String checkPassword, String planetCode) {
        //1.非空校验
        if (StringUtils.isAnyBlank(account, password, checkPassword, planetCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        //2.账户不小于4位
        if (account.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账户过短");
        }
        //3.密码不小于8位
        if (password.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }

        //4.账户不能包含特殊字符
        String validPattern = "[\\u00A0\\s\"`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(account);
        if (matcher.find()) {
            return -1;
        }
        //5.密码和校验密码相同
        if (!password.equals(checkPassword)) {
            return -1;
        }
        //6.账户不能重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        //7.星球编号不能重复
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("planetCode", planetCode);
        count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "星球编号重复");
        }

        //加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + password).getBytes());
        //插入数据
        User user = new User();
        user.setAccount(account);
        user.setPassword(encryptPassword);
        user.setPlanetCode(planetCode);
        boolean saveResult = this.save(user);
        if (!saveResult) {
            return -1;
        }
        return user.getId();
    }

    @Override
    public User userLogin(String account, String password, HttpServletRequest request) {
        //1.非空校验
        if (StringUtils.isAnyBlank(account, password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //2.账户不小于4位
        if (account.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账户过短");
        }
        //3.密码不小于8位
        if (password.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }

        //4.账户不能包含特殊字符
        String validPattern = "[\\u00A0\\s\"`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(account);
        if (matcher.find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账户包含特殊字符");
        }

        //加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + password).getBytes());
        /**
         * 查询用户是否存在
         * 使用mybatis-plus自带的逻辑删除功能
         * 只会查询没有被逻辑删除的数据
         */
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);
        queryWrapper.eq("password", encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            log.info("user login failed, account cannot match password");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账户密码不匹配");
        }

        User safetyUser = getSafetyUser(user);
        //记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATUS, safetyUser);
        return safetyUser;
    }

    @Override
    public User getSafetyUser(User user) {
        if (user == null) {
            return null;
        }
        //用户脱敏
        User safetyUser = new User();
        safetyUser.setId(user.getId());
        safetyUser.setUsername(user.getUsername());
        safetyUser.setAccount(user.getAccount());
        safetyUser.setAvatarUrl(user.getAvatarUrl());
        safetyUser.setGender(user.getGender());
        safetyUser.setPhone(user.getPhone());
        safetyUser.setEmail(user.getEmail());
        safetyUser.setStatus(user.getStatus());
        safetyUser.setPlanetCode(user.getPlanetCode());
        safetyUser.setRole(user.getRole());
        safetyUser.setCreateTime(user.getCreateTime());
        safetyUser.setTags(user.getTags());
        return safetyUser;
    }

    @Override
    public int userLogout(HttpServletRequest request) {
        //移除用户登录态
        request.getSession().removeAttribute(USER_LOGIN_STATUS);
        return 1;
    }

    /**
     *
     * @param tagNameList 用户拥有的标签
     * @return
     */
    @Override
    public List<User> searchUserByTags(List<String> tagNameList) {
        // 为空，抛出用户输入参数异常
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 第一种sql查询(实现简单)
        // 拼接and查询
        // like "%java%" and like "%c++%"
//        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
//        for (String tagName : tagNameList) {
//            queryWrapper.like("tags", tagName);
//        }
        // 第二种内存查询(灵活)
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // 1、先查询所用用户
        List<User> userList = userMapper.selectList(queryWrapper);
        Gson gson = new Gson();
        // 2、然后在内存中运算
        return userList.stream().filter(user -> {
            String tags = user.getTags();
            if (StringUtils.isBlank(tags)) return false;
            // 反序列化
            Set<String> tagNameSet = gson.fromJson(tags, new TypeToken<Set<String>>(){}.getType());
            for (String tagName : tagNameList) {
                if (!tagNameSet.contains(tagName)) {
                    return false;
                }
            }
            return true;
        }).map(this::getSafetyUser).collect(Collectors.toList());
        // 函数式编程this::getSafetyUser
        // return userList.stream().map(this::getSafetyUser).collect(Collectors.toList());
    }

    /**
     *
     * @param tagNameList 用户拥有的标签
     * @return
     */
    @Deprecated
    private List<User> searchUserByTagsBySql(List<String> tagNameList) {
        // 为空，抛出用户输入参数异常
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 第一种sql查询(实现简单)
        // 拼接and查询
        // like "%java%" and like "%c++%"
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        for (String tagName : tagNameList) {
            queryWrapper.like("tags", tagName);
        }
        List<User> userList = userMapper.selectList(queryWrapper);
        // 函数式编程this::getSafetyUser
        return userList.stream().map(this::getSafetyUser).collect(Collectors.toList());
    }
}




