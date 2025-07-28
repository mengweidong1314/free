package com.example.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 高性能缓存配置
 * 支持多级缓存：Caffeine(本地) + Redis(分布式)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Caffeine本地缓存配置
     * 适用于高频访问、数据量较小的场景
     */
    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                // 最大容量
                .maximumSize(10_000)
                // 写入后过期时间
                .expireAfterWrite(30, TimeUnit.MINUTES)
                // 访问后过期时间
                .expireAfterAccess(10, TimeUnit.MINUTES)
                // 统计信息
                .recordStats()
                // 软引用，内存不足时回收
                .softValues());
        return cacheManager;
    }

    /**
     * Redis分布式缓存配置
     * 适用于数据共享、持久化场景
     */
    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 默认过期时间
                .entryTtl(Duration.ofHours(1))
                // 键序列化
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // 值序列化
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                // 不缓存null值
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                // 自定义缓存配置
                .withCacheConfiguration("user", 
                    config.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration("order", 
                    config.entryTtl(Duration.ofHours(2)))
                .build();
    }

    /**
     * 多级缓存管理器
     * 先查本地缓存，再查Redis缓存
     */
    @Bean("multiLevelCacheManager")
    public CacheManager multiLevelCacheManager(CacheManager caffeineCacheManager, 
                                             RedisTemplate<String, Object> redisTemplate) {
        return new MultiLevelCacheManager(caffeineCacheManager, redisTemplate);
    }
}