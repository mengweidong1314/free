package com.example.common.example;

import com.example.common.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 缓存使用示例
 * 展示多级缓存的各种使用场景
 */
@Slf4j
@Component
public class CacheUsageExample {

    @Autowired
    private CacheService cacheService;

    /**
     * 示例1：基本的缓存使用
     */
    public void basicCacheUsage() {
        log.info("=== 基本缓存使用示例 ===");
        
        String key = "user:1";
        String value = "张三";
        
        // 设置缓存
        cacheService.set(key, value, 30, TimeUnit.MINUTES);
        log.info("设置缓存: {} = {}", key, value);
        
        // 获取缓存
        String cachedValue = cacheService.get(key, String.class);
        log.info("获取缓存: {} = {}", key, cachedValue);
        
        // 删除缓存
        cacheService.delete(key);
        log.info("删除缓存: {}", key);
    }

    /**
     * 示例2：缓存穿透保护
     */
    public void cachePenetrationProtection() {
        log.info("=== 缓存穿透保护示例 ===");
        
        String key = "user:999";
        
        // 使用缓存穿透保护
        String user = cacheService.getOrLoad(key, () -> {
            log.info("从数据库加载用户数据: {}", key);
            // 模拟从数据库加载数据
            return "用户" + key;
        }, 30, TimeUnit.MINUTES);
        
        log.info("获取用户: {}", user);
    }

    /**
     * 示例3：批量缓存操作
     */
    public void batchCacheOperations() {
        log.info("=== 批量缓存操作示例 ===");
        
        List<String> keys = List.of("user:1", "user:2", "user:3");
        
        // 批量设置缓存
        java.util.Map<String, String> data = new java.util.HashMap<>();
        data.put("user:1", "张三");
        data.put("user:2", "李四");
        data.put("user:3", "王五");
        
        cacheService.mset(data, 30, TimeUnit.MINUTES);
        log.info("批量设置缓存: {}", data);
        
        // 批量获取缓存
        java.util.Map<String, String> cachedData = cacheService.mget(keys, String.class);
        log.info("批量获取缓存: {}", cachedData);
    }

    /**
     * 示例4：异步缓存操作
     */
    public void asyncCacheOperations() {
        log.info("=== 异步缓存操作示例 ===");
        
        String key = "async:data";
        String value = "异步数据";
        
        // 异步设置缓存
        CompletableFuture<Void> setFuture = cacheService.setAsync(key, value, 30, TimeUnit.MINUTES);
        setFuture.thenRun(() -> log.info("异步设置缓存完成: {}", key));
        
        // 异步获取缓存
        CompletableFuture<String> getFuture = cacheService.getAsync(key, String.class);
        getFuture.thenAccept(cachedValue -> log.info("异步获取缓存: {} = {}", key, cachedValue));
        
        // 等待异步操作完成
        CompletableFuture.allOf(setFuture, getFuture).join();
    }

    /**
     * 示例5：缓存预热
     */
    public void cacheWarmUp() {
        log.info("=== 缓存预热示例 ===");
        
        List<String> keys = List.of("hot:user:1", "hot:user:2", "hot:user:3");
        
        // 缓存预热
        cacheService.warmUp(keys, () -> {
            log.info("执行缓存预热，加载热点数据");
            java.util.Map<String, String> hotData = new java.util.HashMap<>();
            hotData.put("hot:user:1", "热点用户1");
            hotData.put("hot:user:2", "热点用户2");
            hotData.put("hot:user:3", "热点用户3");
            return hotData;
        });
    }

    /**
     * 示例6：使用@Cacheable注解
     */
    @Cacheable(value = "user", key = "#userId", unless = "#result == null")
    public String getUserById(Long userId) {
        log.info("从数据库查询用户: {}", userId);
        // 模拟数据库查询
        return "用户" + userId;
    }

    /**
     * 示例7：缓存统计信息
     */
    public void cacheStatistics() {
        log.info("=== 缓存统计信息示例 ===");
        
        java.util.Map<String, Object> stats = cacheService.getCacheStats();
        log.info("缓存统计信息: {}", stats);
    }

    /**
     * 示例8：性能测试
     */
    public void performanceTest() {
        log.info("=== 缓存性能测试示例 ===");
        
        String key = "perf:test";
        String value = "性能测试数据";
        
        // 设置测试数据
        cacheService.set(key, value, 5, TimeUnit.MINUTES);
        
        // 测试读取性能
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            cacheService.get(key, String.class);
        }
        long endTime = System.currentTimeMillis();
        
        log.info("1000次缓存读取耗时: {}ms", endTime - startTime);
        log.info("平均每次读取耗时: {}ms", (endTime - startTime) / 1000.0);
    }

    /**
     * 示例9：缓存失效策略
     */
    public void cacheEvictionStrategy() {
        log.info("=== 缓存失效策略示例 ===");
        
        // 设置不同过期时间的数据
        cacheService.set("short:data", "短期数据", 1, TimeUnit.MINUTES);
        cacheService.set("medium:data", "中期数据", 30, TimeUnit.MINUTES);
        cacheService.set("long:data", "长期数据", 2, TimeUnit.HOURS);
        
        log.info("设置不同过期时间的缓存数据");
        
        // 等待短期数据过期
        try {
            Thread.sleep(70 * 1000); // 等待70秒
            String shortData = cacheService.get("short:data", String.class);
            log.info("短期数据: {}", shortData); // 应该为null
            
            String mediumData = cacheService.get("medium:data", String.class);
            log.info("中期数据: {}", mediumData); // 应该还有值
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 示例10：错误处理
     */
    public void errorHandling() {
        log.info("=== 缓存错误处理示例 ===");
        
        // 测试Redis连接失败的情况
        String key = "error:test";
        
        try {
            // 这里可能会因为Redis连接问题而失败
            cacheService.set(key, "测试数据", 30, TimeUnit.MINUTES);
            log.info("缓存设置成功");
        } catch (Exception e) {
            log.error("缓存设置失败: {}", e.getMessage());
        }
        
        try {
            String value = cacheService.get(key, String.class);
            log.info("缓存获取结果: {}", value);
        } catch (Exception e) {
            log.error("缓存获取失败: {}", e.getMessage());
        }
    }

    /**
     * 运行所有示例
     */
    public void runAllExamples() {
        log.info("开始运行缓存使用示例...");
        
        try {
            basicCacheUsage();
            Thread.sleep(1000);
            
            cachePenetrationProtection();
            Thread.sleep(1000);
            
            batchCacheOperations();
            Thread.sleep(1000);
            
            asyncCacheOperations();
            Thread.sleep(1000);
            
            cacheWarmUp();
            Thread.sleep(1000);
            
            // 测试@Cacheable注解
            String user1 = getUserById(1L);
            String user2 = getUserById(1L); // 第二次调用应该从缓存获取
            log.info("用户1: {}, 用户2: {}", user1, user2);
            Thread.sleep(1000);
            
            cacheStatistics();
            Thread.sleep(1000);
            
            performanceTest();
            Thread.sleep(1000);
            
            cacheEvictionStrategy();
            Thread.sleep(1000);
            
            errorHandling();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("示例执行被中断", e);
        }
        
        log.info("缓存使用示例执行完成");
    }
}