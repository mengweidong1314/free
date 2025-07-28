package com.example.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * 自定义多级缓存管理器
 * 实现真正的多级缓存：Caffeine(本地) + Redis(分布式)
 */
@Slf4j
@Component
public class MultiLevelCacheManager implements CacheManager {

    private final CacheManager caffeineCacheManager;
    private final RedisTemplate<String, Object> redisTemplate;

    public MultiLevelCacheManager(CacheManager caffeineCacheManager, RedisTemplate<String, Object> redisTemplate) {
        this.caffeineCacheManager = caffeineCacheManager;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Cache getCache(String name) {
        Cache caffeineCache = caffeineCacheManager.getCache(name);
        return new MultiLevelCache(name, caffeineCache, redisTemplate);
    }

    @Override
    public Collection<String> getCacheNames() {
        return caffeineCacheManager.getCacheNames();
    }

    /**
     * 多级缓存实现
     */
    @Slf4j
    private static class MultiLevelCache implements Cache {

        private final String name;
        private final Cache localCache;
        private final RedisTemplate<String, Object> redisTemplate;

        public MultiLevelCache(String name, Cache localCache, RedisTemplate<String, Object> redisTemplate) {
            this.name = name;
            this.localCache = localCache;
            this.redisTemplate = redisTemplate;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return localCache.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            log.debug("多级缓存查询，cache: {}, key: {}", name, key);
            
            // 1. 先查本地缓存
            ValueWrapper localValue = localCache.get(key);
            if (localValue != null) {
                log.debug("本地缓存命中，cache: {}, key: {}", name, key);
                return localValue;
            }

            // 2. 本地缓存未命中，查Redis缓存
            String redisKey = generateRedisKey(key);
            Object redisValue = redisTemplate.opsForValue().get(redisKey);
            if (redisValue != null) {
                log.debug("Redis缓存命中，cache: {}, key: {}", name, key);
                // 将Redis的数据同步到本地缓存
                localCache.put(key, redisValue);
                return new SimpleValueWrapper(redisValue);
            }

            log.debug("缓存未命中，cache: {}, key: {}", name, key);
            return null;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            log.debug("多级缓存查询(带类型)，cache: {}, key: {}, type: {}", name, key, type.getSimpleName());
            
            // 1. 先查本地缓存
            T localValue = localCache.get(key, type);
            if (localValue != null) {
                log.debug("本地缓存命中，cache: {}, key: {}", name, key);
                return localValue;
            }

            // 2. 本地缓存未命中，查Redis缓存
            String redisKey = generateRedisKey(key);
            Object redisValue = redisTemplate.opsForValue().get(redisKey);
            if (redisValue != null && type.isInstance(redisValue)) {
                log.debug("Redis缓存命中，cache: {}, key: {}", name, key);
                T typedValue = type.cast(redisValue);
                // 将Redis的数据同步到本地缓存
                localCache.put(key, typedValue);
                return typedValue;
            }

            log.debug("缓存未命中，cache: {}, key: {}", name, key);
            return null;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            log.debug("多级缓存查询(带加载器)，cache: {}, key: {}", name, key);
            
            // 1. 先查本地缓存
            T localValue = localCache.get(key, valueLoader);
            if (localValue != null) {
                log.debug("本地缓存命中，cache: {}, key: {}", name, key);
                return localValue;
            }

            // 2. 本地缓存未命中，查Redis缓存
            String redisKey = generateRedisKey(key);
            Object redisValue = redisTemplate.opsForValue().get(redisKey);
            if (redisValue != null) {
                log.debug("Redis缓存命中，cache: {}, key: {}", name, key);
                @SuppressWarnings("unchecked")
                T typedValue = (T) redisValue;
                // 将Redis的数据同步到本地缓存
                localCache.put(key, typedValue);
                return typedValue;
            }

            // 3. Redis也未命中，使用加载器加载数据
            try {
                log.debug("从数据源加载数据，cache: {}, key: {}", name, key);
                T loadedValue = valueLoader.call();
                if (loadedValue != null) {
                    // 同时更新本地缓存和Redis缓存
                    put(key, loadedValue);
                }
                return loadedValue;
            } catch (Exception e) {
                log.error("从数据源加载数据失败，cache: {}, key: {}", name, key, e);
                throw new RuntimeException("Failed to load value for key: " + key, e);
            }
        }

        @Override
        public void put(Object key, Object value) {
            log.debug("多级缓存写入，cache: {}, key: {}", name, key);
            
            // 1. 写入本地缓存
            localCache.put(key, value);
            
            // 2. 写入Redis缓存
            String redisKey = generateRedisKey(key);
            try {
                redisTemplate.opsForValue().set(redisKey, value, 30, java.util.concurrent.TimeUnit.MINUTES);
                log.debug("数据已写入多级缓存，cache: {}, key: {}", name, key);
            } catch (Exception e) {
                log.error("写入Redis缓存失败，cache: {}, key: {}", name, key, e);
            }
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            log.debug("多级缓存条件写入，cache: {}, key: {}", name, key);
            
            // 1. 检查本地缓存
            ValueWrapper existingValue = localCache.get(key);
            if (existingValue != null) {
                log.debug("本地缓存已存在，不写入，cache: {}, key: {}", name, key);
                return existingValue;
            }

            // 2. 检查Redis缓存
            String redisKey = generateRedisKey(key);
            Object redisValue = redisTemplate.opsForValue().get(redisKey);
            if (redisValue != null) {
                log.debug("Redis缓存已存在，同步到本地缓存，cache: {}, key: {}", name, key);
                localCache.put(key, redisValue);
                return new SimpleValueWrapper(redisValue);
            }

            // 3. 都不存在，写入数据
            put(key, value);
            return null;
        }

        @Override
        public void evict(Object key) {
            log.debug("多级缓存删除，cache: {}, key: {}", name, key);
            
            // 1. 删除本地缓存
            localCache.evict(key);
            
            // 2. 删除Redis缓存
            String redisKey = generateRedisKey(key);
            try {
                redisTemplate.delete(redisKey);
                log.debug("数据已从多级缓存删除，cache: {}, key: {}", name, key);
            } catch (Exception e) {
                log.error("删除Redis缓存失败，cache: {}, key: {}", name, key, e);
            }
        }

        @Override
        public void clear() {
            log.debug("清空多级缓存，cache: {}", name);
            
            // 1. 清空本地缓存
            localCache.clear();
            
            // 2. 清空Redis缓存（这里只能清空整个数据库，实际使用时需要更精确的控制）
            try {
                // 注意：这里会清空整个Redis数据库，生产环境需要更精确的控制
                // redisTemplate.getConnectionFactory().getConnection().flushDb();
                log.warn("Redis缓存清空功能需要更精确的实现，cache: {}", name);
            } catch (Exception e) {
                log.error("清空Redis缓存失败，cache: {}", name, e);
            }
        }

        /**
         * 生成Redis键
         */
        private String generateRedisKey(Object key) {
            return String.format("cache:%s:%s", name, key.toString());
        }

        /**
         * 简单的值包装器
         */
        private static class SimpleValueWrapper implements ValueWrapper {
            private final Object value;

            public SimpleValueWrapper(Object value) {
                this.value = value;
            }

            @Override
            public Object get() {
                return value;
            }
        }
    }
}