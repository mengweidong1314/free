# 多级缓存实现详解

## 1. 概述

本项目实现了真正的多级缓存架构，包含以下层级：
- **L1缓存**: Caffeine本地缓存（内存）
- **L2缓存**: Redis分布式缓存（网络）

## 2. 核心组件

### 2.1 MultiLevelCacheManager
自定义的缓存管理器，实现Spring Cache的CacheManager接口。

```java
@Component
public class MultiLevelCacheManager implements CacheManager {
    private final CacheManager caffeineCacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Override
    public Cache getCache(String name) {
        Cache caffeineCache = caffeineCacheManager.getCache(name);
        return new MultiLevelCache(name, caffeineCache, redisTemplate);
    }
}
```

### 2.2 MultiLevelCache
具体的多级缓存实现，实现Spring Cache的Cache接口。

```java
private static class MultiLevelCache implements Cache {
    private final String name;
    private final Cache localCache;        // Caffeine缓存
    private final RedisTemplate<String, Object> redisTemplate;  // Redis操作模板
}
```

## 3. 缓存查询流程

### 3.1 读取流程
```
1. 查询L1缓存(Caffeine)
   ↓
2. L1命中？ → 返回数据
   ↓ 否
3. 查询L2缓存(Redis)
   ↓
4. L2命中？ → 同步到L1 → 返回数据
   ↓ 否
5. 返回null
```

### 3.2 写入流程
```
1. 写入L1缓存(Caffeine)
   ↓
2. 写入L2缓存(Redis)
   ↓
3. 完成
```

### 3.3 删除流程
```
1. 删除L1缓存(Caffeine)
   ↓
2. 删除L2缓存(Redis)
   ↓
3. 完成
```

## 4. 核心方法实现

### 4.1 读取方法
```java
@Override
public ValueWrapper get(Object key) {
    // 1. 先查本地缓存
    ValueWrapper localValue = localCache.get(key);
    if (localValue != null) {
        return localValue;
    }

    // 2. 本地缓存未命中，查Redis缓存
    String redisKey = generateRedisKey(key);
    Object redisValue = redisTemplate.opsForValue().get(redisKey);
    if (redisValue != null) {
        // 将Redis的数据同步到本地缓存
        localCache.put(key, redisValue);
        return new SimpleValueWrapper(redisValue);
    }

    return null;
}
```

### 4.2 写入方法
```java
@Override
public void put(Object key, Object value) {
    // 1. 写入本地缓存
    localCache.put(key, value);
    
    // 2. 写入Redis缓存
    String redisKey = generateRedisKey(key);
    redisTemplate.opsForValue().set(redisKey, value, 30, TimeUnit.MINUTES);
}
```

### 4.3 删除方法
```java
@Override
public void evict(Object key) {
    // 1. 删除本地缓存
    localCache.evict(key);
    
    // 2. 删除Redis缓存
    String redisKey = generateRedisKey(key);
    redisTemplate.delete(redisKey);
}
```

## 5. 配置说明

### 5.1 缓存配置
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .recordStats()
                .softValues());
        return cacheManager;
    }
    
    @Bean("multiLevelCacheManager")
    public CacheManager multiLevelCacheManager(CacheManager caffeineCacheManager, 
                                             RedisTemplate<String, Object> redisTemplate) {
        return new MultiLevelCacheManager(caffeineCacheManager, redisTemplate);
    }
}
```

### 5.2 使用方式
```java
@Service
public class CacheService {
    
    @Cacheable(value = "common", key = "#key", unless = "#result == null", 
               cacheManager = "multiLevelCacheManager")
    public <T> T get(String key, Class<T> clazz) {
        // 实际的缓存逻辑在MultiLevelCacheManager中实现
        return null;
    }
}
```

## 6. 性能优化

### 6.1 缓存策略
- **L1缓存**: 高频访问数据，容量较小，过期时间短
- **L2缓存**: 低频访问数据，容量较大，过期时间长

### 6.2 同步策略
- **写时同步**: 写入时同时更新L1和L2缓存
- **读时同步**: 读取L2缓存时自动同步到L1缓存

### 6.3 失效策略
- **同时失效**: 删除时同时清除L1和L2缓存
- **延迟失效**: 可以设置L1缓存的过期时间比L2短

## 7. 监控和统计

### 7.1 缓存命中率
```java
public java.util.Map<String, Object> getCacheStats() {
    java.util.Map<String, Object> stats = new java.util.HashMap<>();
    
    // Redis统计信息
    stats.put("redis_keys", redisTemplate.getConnectionFactory().getConnection().dbSize());
    
    // Caffeine统计信息（需要获取Caffeine的统计信息）
    // stats.put("caffeine_stats", caffeineCache.getNativeCache().stats());
    
    return stats;
}
```

### 7.2 性能监控
- 缓存命中率
- 平均响应时间
- 缓存大小
- 过期时间分布

## 8. 错误处理

### 8.1 异常处理
```java
try {
    Object redisValue = redisTemplate.opsForValue().get(redisKey);
    // 处理Redis数据
} catch (Exception e) {
    log.error("从Redis获取数据失败，key: {}, error: {}", key, e.getMessage());
    // 降级处理：只使用本地缓存
    return localCache.get(key);
}
```

### 8.2 降级策略
- **Redis故障**: 只使用本地缓存
- **网络超时**: 设置合理的超时时间
- **数据不一致**: 实现数据校验机制

## 9. 使用示例

### 9.1 基本使用
```java
// 设置缓存
cacheService.set("user:1", user, 30, TimeUnit.MINUTES);

// 获取缓存
User user = cacheService.get("user:1", User.class);

// 删除缓存
cacheService.delete("user:1");
```

### 9.2 缓存穿透保护
```java
String user = cacheService.getOrLoad("user:999", () -> {
    // 从数据库加载数据
    return userRepository.findById(999L);
}, 30, TimeUnit.MINUTES);
```

### 9.3 批量操作
```java
// 批量设置
Map<String, User> users = new HashMap<>();
users.put("user:1", user1);
users.put("user:2", user2);
cacheService.mset(users, 30, TimeUnit.MINUTES);

// 批量获取
List<String> keys = Arrays.asList("user:1", "user:2");
Map<String, User> cachedUsers = cacheService.mget(keys, User.class);
```

### 9.4 异步操作
```java
// 异步设置
CompletableFuture<Void> future = cacheService.setAsync("user:1", user, 30, TimeUnit.MINUTES);

// 异步获取
CompletableFuture<User> userFuture = cacheService.getAsync("user:1", User.class);
```

## 10. 最佳实践

### 10.1 缓存键设计
- 使用有意义的键名
- 避免键名冲突
- 合理设置过期时间

### 10.2 数据一致性
- 实现缓存更新策略
- 处理并发更新
- 定期清理过期数据

### 10.3 性能调优
- 监控缓存命中率
- 调整缓存大小
- 优化过期策略

### 10.4 故障处理
- 实现降级机制
- 监控缓存状态
- 设置告警阈值

## 11. 测试验证

### 11.1 功能测试
```bash
# 设置缓存
curl -X POST "http://localhost:8081/api/cache/set?key=test&value=hello"

# 获取缓存
curl "http://localhost:8081/api/cache/get/test"

# 删除缓存
curl -X DELETE "http://localhost:8081/api/cache/delete/test"
```

### 11.2 性能测试
```bash
# 性能测试
curl "http://localhost:8081/api/cache/performance"

# 统计信息
curl "http://localhost:8081/api/cache/stats"
```

### 11.3 压力测试
```bash
# 使用wrk进行压力测试
wrk -t12 -c400 -d30s http://localhost:8081/api/cache/get/test
```

## 12. 总结

多级缓存实现提供了以下优势：

1. **高性能**: L1缓存提供毫秒级响应
2. **高可用**: L2缓存提供数据持久化
3. **可扩展**: 支持水平扩展
4. **易维护**: 基于Spring Cache标准接口
5. **可监控**: 提供完整的监控指标

通过合理配置和使用，多级缓存可以显著提升系统性能，减少数据库压力，提高用户体验。