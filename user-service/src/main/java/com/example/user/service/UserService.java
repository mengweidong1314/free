package com.example.user.service;

import com.example.common.service.AsyncService;
import com.example.common.service.CacheService;
import com.example.user.entity.User;
import com.example.user.mapper.UserMapper;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 高性能用户服务
 * 包含缓存、异步、限流、监控等特性
 */
@Slf4j
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private AsyncService asyncService;

    /**
     * 根据ID获取用户（带缓存）
     */
    @SentinelResource(value = "user-service", blockHandler = "getUserBlockHandler")
    @Cacheable(value = "user", key = "#id", unless = "#result == null")
    @Timed(value = "user.get.by.id", description = "根据ID获取用户")
    public User getUserById(Long id) {
        log.info("从数据库获取用户，ID: {}", id);
        return userMapper.selectById(id);
    }

    /**
     * 异步获取用户
     */
    public CompletableFuture<User> getUserByIdAsync(Long id) {
        return asyncService.executeAsync(() -> getUserById(id));
    }

    /**
     * 批量获取用户（高性能）
     */
    public List<User> getUsersByIds(List<Long> ids) {
        // 先尝试从缓存获取
        List<String> cacheKeys = ids.stream()
                .map(id -> "user:" + id)
                .toList();

        return cacheService.mget(cacheKeys, User.class)
                .values()
                .stream()
                .toList();
    }

    /**
     * 异步批量获取用户
     */
    public CompletableFuture<List<User>> getUsersByIdsAsync(List<Long> ids) {
        return asyncService.executeAsync(() -> getUsersByIds(ids));
    }

    /**
     * 创建用户（异步缓存更新）
     */
    @SentinelResource(value = "user-service", blockHandler = "createUserBlockHandler")
    @Timed(value = "user.create", description = "创建用户")
    public User createUser(User user) {
        log.info("创建用户: {}", user.getUsername());
        
        // 异步处理用户创建后的操作
        asyncService.executeAsync(() -> {
            // 发送欢迎邮件
            sendWelcomeEmail(user);
            // 初始化用户数据
            initializeUserData(user);
            return null;
        });

        return userMapper.insert(user) > 0 ? user : null;
    }

    /**
     * 更新用户（缓存失效）
     */
    @SentinelResource(value = "user-service", blockHandler = "updateUserBlockHandler")
    @CacheEvict(value = "user", key = "#user.id")
    @Timed(value = "user.update", description = "更新用户")
    public boolean updateUser(User user) {
        log.info("更新用户: {}", user.getId());
        
        // 异步更新相关数据
        asyncService.executeAsync(() -> {
            updateUserRelatedData(user);
            return null;
        });

        return userMapper.updateById(user) > 0;
    }

    /**
     * 删除用户（缓存失效）
     */
    @SentinelResource(value = "user-service", blockHandler = "deleteUserBlockHandler")
    @CacheEvict(value = "user", key = "#id")
    @Timed(value = "user.delete", description = "删除用户")
    public boolean deleteUser(Long id) {
        log.info("删除用户: {}", id);
        
        // 异步清理相关数据
        asyncService.executeAsync(() -> {
            cleanupUserData(id);
            return null;
        });

        return userMapper.deleteById(id) > 0;
    }

    /**
     * 搜索用户（高性能）
     */
    @SentinelResource(value = "user-service", blockHandler = "searchUsersBlockHandler")
    @Timed(value = "user.search", description = "搜索用户")
    public List<User> searchUsers(String keyword) {
        // 使用缓存减少数据库压力
        String cacheKey = "user:search:" + keyword;
        return cacheService.getOrLoad(cacheKey, 
                () -> userMapper.searchUsers(keyword), 
                30, TimeUnit.MINUTES);
    }

    /**
     * 异步搜索用户
     */
    public CompletableFuture<List<User>> searchUsersAsync(String keyword) {
        return asyncService.executeAsync(() -> searchUsers(keyword));
    }

    /**
     * 用户统计（缓存）
     */
    @Cacheable(value = "user", key = "'stats'")
    @Timed(value = "user.stats", description = "用户统计")
    public Long getUserCount() {
        return userMapper.selectCount(null);
    }

    // Sentinel 限流熔断处理方法
    public User getUserBlockHandler(Long id, BlockException ex) {
        log.warn("用户服务被限流，ID: {}", id);
        return null;
    }

    public User createUserBlockHandler(User user, BlockException ex) {
        log.warn("用户服务被限流，创建用户失败");
        return null;
    }

    public boolean updateUserBlockHandler(User user, BlockException ex) {
        log.warn("用户服务被限流，更新用户失败");
        return false;
    }

    public boolean deleteUserBlockHandler(Long id, BlockException ex) {
        log.warn("用户服务被限流，删除用户失败");
        return false;
    }

    public List<User> searchUsersBlockHandler(String keyword, BlockException ex) {
        log.warn("用户服务被限流，搜索用户失败");
        return List.of();
    }

    // 私有方法
    private void sendWelcomeEmail(User user) {
        log.info("发送欢迎邮件给用户: {}", user.getEmail());
        // 模拟邮件发送
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void initializeUserData(User user) {
        log.info("初始化用户数据: {}", user.getId());
        // 模拟数据初始化
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateUserRelatedData(User user) {
        log.info("更新用户相关数据: {}", user.getId());
        // 模拟更新相关数据
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void cleanupUserData(Long userId) {
        log.info("清理用户数据: {}", userId);
        // 模拟数据清理
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}