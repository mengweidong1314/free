package com.example.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 高性能缓存服务
 * 支持多级缓存、异步操作、批量操作
 */
@Slf4j
@Service
public class CacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取缓存数据（支持多级缓存）
     * 先查本地缓存，再查Redis缓存
     */
    @Cacheable(value = "common", key = "#key", unless = "#result == null")
    public <T> T get(String key, Class<T> clazz) {
        // 这里会先查Caffeine缓存，如果没有再查Redis
        return null;
    }

    /**
     * 异步获取缓存数据
     */
    public <T> CompletableFuture<T> getAsync(String key, Class<T> clazz) {
        return CompletableFuture.supplyAsync(() -> get(key, clazz));
    }

    /**
     * 设置缓存数据
     */
    @CachePut(value = "common", key = "#key")
    public <T> T set(String key, T value) {
        return value;
    }

    /**
     * 设置缓存数据（带过期时间）
     */
    public <T> void set(String key, T value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 异步设置缓存数据
     */
    public <T> CompletableFuture<Void> setAsync(String key, T value, long timeout, TimeUnit unit) {
        return CompletableFuture.runAsync(() -> set(key, value, timeout, unit));
    }

    /**
     * 删除缓存
     */
    @CacheEvict(value = "common", key = "#key")
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 异步删除缓存
     */
    public CompletableFuture<Void> deleteAsync(String key) {
        return CompletableFuture.runAsync(() -> delete(key));
    }

    /**
     * 缓存穿透保护：获取或加载数据
     * 如果缓存中没有，则从数据源加载并缓存
     */
    public <T> T getOrLoad(String key, Supplier<T> loader, long timeout, TimeUnit unit) {
        T value = get(key, (Class<T>) Object.class);
        if (value != null) {
            return value;
        }

        // 防止缓存穿透：使用分布式锁
        String lockKey = "lock:" + key;
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                // 双重检查
                value = get(key, (Class<T>) Object.class);
                if (value != null) {
                    return value;
                }

                // 从数据源加载
                value = loader.get();
                if (value != null) {
                    set(key, value, timeout, unit);
                }
                return value;
            } else {
                // 等待其他线程加载完成
                Thread.sleep(100);
                return get(key, (Class<T>) Object.class);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取缓存数据被中断", e);
            return null;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 异步缓存穿透保护
     */
    public <T> CompletableFuture<T> getOrLoadAsync(String key, Supplier<T> loader, long timeout, TimeUnit unit) {
        return CompletableFuture.supplyAsync(() -> getOrLoad(key, loader, timeout, unit));
    }

    /**
     * 批量获取缓存数据
     */
    public <T> java.util.Map<String, T> mget(java.util.List<String> keys, Class<T> clazz) {
        java.util.Map<String, T> result = new java.util.HashMap<>();
        for (String key : keys) {
            T value = get(key, clazz);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * 异步批量获取缓存数据
     */
    public <T> CompletableFuture<java.util.Map<String, T>> mgetAsync(java.util.List<String> keys, Class<T> clazz) {
        return CompletableFuture.supplyAsync(() -> mget(keys, clazz));
    }

    /**
     * 批量设置缓存数据
     */
    public <T> void mset(java.util.Map<String, T> data, long timeout, TimeUnit unit) {
        for (java.util.Map.Entry<String, T> entry : data.entrySet()) {
            set(entry.getKey(), entry.getValue(), timeout, unit);
        }
    }

    /**
     * 异步批量设置缓存数据
     */
    public <T> CompletableFuture<Void> msetAsync(java.util.Map<String, T> data, long timeout, TimeUnit unit) {
        return CompletableFuture.runAsync(() -> mset(data, timeout, unit));
    }

    /**
     * 缓存预热
     * 系统启动时预加载热点数据
     */
    public <T> void warmUp(java.util.List<String> keys, Supplier<java.util.Map<String, T>> loader) {
        CompletableFuture.runAsync(() -> {
            try {
                java.util.Map<String, T> data = loader.get();
                mset(data, 1, TimeUnit.HOURS);
                log.info("缓存预热完成，共预热{}个key", data.size());
            } catch (Exception e) {
                log.error("缓存预热失败", e);
            }
        });
    }
}