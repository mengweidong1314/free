package com.example.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
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
     * 获取缓存数据（多级缓存实现）
     * 使用自定义的多级缓存管理器
     */
    @Cacheable(value = "common", key = "#key", unless = "#result == null", cacheManager = "multiLevelCacheManager")
    public <T> T get(String key, Class<T> clazz) {
        log.debug("查询多级缓存，key: {}, type: {}", key, clazz.getSimpleName());
        
        // 这里的方法体可以为空，因为实际的缓存逻辑在MultiLevelCacheManager中实现
        // 或者可以保留原有的Redis查询逻辑作为备用
        T redisValue = getFromRedis(key, clazz);
        if (redisValue != null) {
            log.debug("从Redis获取到数据，key: {}", key);
            return redisValue;
        }
        
        log.debug("Redis中未找到数据，key: {}", key);
        return null;
    }

    /**
     * 从Redis获取数据
     */
    private <T> T getFromRedis(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                // 如果Redis中的值是JSON字符串，需要反序列化
                if (value instanceof String) {
                    return deserializeFromJson((String) value, clazz);
                }
                // 如果Redis中的值已经是对象，直接转换
                return clazz.cast(value);
            }
        } catch (Exception e) {
            log.error("从Redis获取数据失败，key: {}, error: {}", key, e.getMessage());
        }
        return null;
    }

    /**
     * 设置缓存数据（同时更新本地缓存和Redis）
     */
    @CachePut(value = "common", key = "#key", cacheManager = "multiLevelCacheManager")
    public <T> T set(String key, T value) {
        log.debug("设置多级缓存，key: {}, value: {}", key, value);
        
        // 1. 先更新Redis缓存
        setToRedis(key, value);
        
        // 2. 返回value，Spring会自动更新本地缓存
        return value;
    }

    /**
     * 设置数据到Redis
     */
    private <T> void setToRedis(String key, T value) {
        try {
            if (value != null) {
                // 设置到Redis，过期时间30分钟
                redisTemplate.opsForValue().set(key, value, 30, TimeUnit.MINUTES);
                log.debug("数据已设置到Redis，key: {}", key);
            }
        } catch (Exception e) {
            log.error("设置数据到Redis失败，key: {}, error: {}", key, e.getMessage());
        }
    }

    /**
     * 设置缓存数据（带过期时间）
     */
    public <T> void set(String key, T value, long timeout, TimeUnit unit) {
        log.debug("设置缓存数据，key: {}, timeout: {} {}", key, timeout, unit);
        
        try {
            // 1. 设置到Redis
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            
            // 2. 更新本地缓存（通过Spring Cache注解）
            set(key, value);
            
        } catch (Exception e) {
            log.error("设置缓存数据失败，key: {}, error: {}", key, e.getMessage());
        }
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
    @CacheEvict(value = "common", key = "#key", cacheManager = "multiLevelCacheManager")
    public void delete(String key) {
        log.debug("删除缓存，key: {}", key);
        
        try {
            // 1. 删除Redis缓存
            redisTemplate.delete(key);
            
            // 2. Spring会自动删除本地缓存（通过@CacheEvict注解）
            
        } catch (Exception e) {
            log.error("删除缓存失败，key: {}, error: {}", key, e.getMessage());
        }
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
        log.debug("获取或加载数据，key: {}", key);
        
        // 1. 先尝试从缓存获取
        T value = get(key, (Class<T>) Object.class);
        if (value != null) {
            log.debug("从缓存获取到数据，key: {}", key);
            return value;
        }

        // 2. 防止缓存穿透：使用分布式锁
        String lockKey = "lock:" + key;
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                // 双重检查
                value = get(key, (Class<T>) Object.class);
                if (value != null) {
                    log.debug("双重检查发现缓存已有数据，key: {}", key);
                    return value;
                }

                // 3. 从数据源加载
                log.debug("从数据源加载数据，key: {}", key);
                value = loader.get();
                if (value != null) {
                    set(key, value, timeout, unit);
                    log.debug("数据已加载并缓存，key: {}", key);
                }
                return value;
            } else {
                // 4. 等待其他线程加载完成
                log.debug("等待其他线程加载数据，key: {}", key);
                Thread.sleep(100);
                return get(key, (Class<T>) Object.class);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取缓存数据被中断，key: {}", key);
            return null;
        } finally {
            // 5. 释放分布式锁
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
    public <T> java.util.Map<String, T> mget(List<String> keys, Class<T> clazz) {
        log.debug("批量获取缓存数据，keys: {}", keys);
        
        java.util.Map<String, T> result = new java.util.HashMap<>();
        
        for (String key : keys) {
            T value = get(key, clazz);
            if (value != null) {
                result.put(key, value);
            }
        }
        
        log.debug("批量获取完成，命中数量: {}/{}", result.size(), keys.size());
        return result;
    }

    /**
     * 异步批量获取缓存数据
     */
    public <T> CompletableFuture<java.util.Map<String, T>> mgetAsync(List<String> keys, Class<T> clazz) {
        return CompletableFuture.supplyAsync(() -> mget(keys, clazz));
    }

    /**
     * 批量设置缓存数据
     */
    public <T> void mset(java.util.Map<String, T> data, long timeout, TimeUnit unit) {
        log.debug("批量设置缓存数据，数量: {}", data.size());
        
        for (java.util.Map.Entry<String, T> entry : data.entrySet()) {
            set(entry.getKey(), entry.getValue(), timeout, unit);
        }
        
        log.debug("批量设置完成");
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
    public <T> void warmUp(List<String> keys, Supplier<java.util.Map<String, T>> loader) {
        log.info("开始缓存预热，keys数量: {}", keys.size());
        
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

    /**
     * 获取缓存统计信息
     */
    public java.util.Map<String, Object> getCacheStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        try {
            // Redis统计信息
            stats.put("redis_keys", redisTemplate.getConnectionFactory().getConnection().dbSize());
            
            // 可以添加更多统计信息
            // 比如Caffeine缓存的统计信息等
            
        } catch (Exception e) {
            log.error("获取缓存统计信息失败", e);
        }
        
        return stats;
    }

    /**
     * 清空所有缓存
     */
    public void clearAll() {
        log.warn("清空所有缓存");
        
        try {
            // 清空Redis缓存
            redisTemplate.getConnectionFactory().getConnection().flushDb();
            
            // 注意：这里无法直接清空Caffeine缓存
            // 需要通过Spring Cache的CacheManager来清空
            // 或者重启应用
            
        } catch (Exception e) {
            log.error("清空缓存失败", e);
        }
    }

    /**
     * JSON反序列化（简化实现）
     */
    private <T> T deserializeFromJson(String json, Class<T> clazz) {
        try {
            // 这里可以使用Jackson、Gson等JSON库
            // 为了简化，这里返回null，实际使用时需要实现具体的反序列化逻辑
            log.warn("JSON反序列化功能需要实现，json: {}", json);
            return null;
        } catch (Exception e) {
            log.error("JSON反序列化失败", e);
            return null;
        }
    }
}