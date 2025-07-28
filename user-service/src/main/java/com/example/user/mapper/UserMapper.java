package com.example.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 搜索用户
     */
    @Select("SELECT * FROM users WHERE username LIKE CONCAT('%', #{keyword}, '%') " +
            "OR email LIKE CONCAT('%', #{keyword}, '%') " +
            "OR phone LIKE CONCAT('%', #{keyword}, '%')")
    List<User> searchUsers(@Param("keyword") String keyword);

    /**
     * 根据邮箱获取用户
     */
    @Select("SELECT * FROM users WHERE email = #{email}")
    User getUserByEmail(@Param("email") String email);

    /**
     * 根据手机号获取用户
     */
    @Select("SELECT * FROM users WHERE phone = #{phone}")
    User getUserByPhone(@Param("phone") String phone);
}