package com.example.user.controller;

import com.example.common.service.CacheService;
import com.example.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 缓存测试控制器
 * 用于演示多级缓存的使用
 */
@Slf4j
@RestController
@RequestMapping("/api/cache")
public class CacheTestController {

    @Autowired
    private CacheService cacheService;

    /**
     * 测试基本缓存操作
     */
    @PostMapping("/set")
    public ResponseEntity<String> setCache(@RequestParam String key, @RequestParam String value) {
        try {
            cacheService.set(key, value, 30, TimeUnit.MINUTES);
            return ResponseEntity.ok("缓存设置成功: " + key + " = " + value);
        } catch (Exception e) {
            log.error("设置缓存失败", e);
            return ResponseEntity.internalServerError().body("设置缓存失败: " + e.getMessage());
        }
    }

    /**
     * 测试缓存获取
     */
    @GetMapping("/get/{key}")
    public ResponseEntity<String> getCache(@PathVariable String key) {
        try {
            String value = cacheService.get(key, String.class);
            if (value != null) {
                return ResponseEntity.ok("缓存命中: " + key + " = " + value);
            } else {
                return ResponseEntity.ok("缓存未命中: " + key);
            }
        } catch (Exception e) {
            log.error("获取缓存失败", e);
            return ResponseEntity.internalServerError().body("获取缓存失败: " + e.getMessage());
        }
    }

    /**
     * 测试缓存删除
     */
    @DeleteMapping("/delete/{key}")
    public ResponseEntity<String> deleteCache(@PathVariable String key) {
        try {
            cacheService.delete(key);
            return ResponseEntity.ok("缓存删除成功: " + key);
        } catch (Exception e) {
            log.error("删除缓存失败", e);
            return ResponseEntity.internalServerError().body("删除缓存失败: " + e.getMessage());
        }
    }

    /**
     * 测试缓存穿透保护
     */
    @GetMapping("/get-or-load/{key}")
    public ResponseEntity<String> getOrLoad(@PathVariable String key) {
        try {
            String value = cacheService.getOrLoad(key, () -> {
                log.info("从数据源加载数据: {}", key);
                return "加载的数据: " + key;
            }, 30, TimeUnit.MINUTES);
            
            return ResponseEntity.ok("获取结果: " + value);
        } catch (Exception e) {
            log.error("获取或加载数据失败", e);
            return ResponseEntity.internalServerError().body("获取或加载数据失败: " + e.getMessage());
        }
    }

    /**
     * 测试异步缓存操作
     */
    @PostMapping("/async-set")
    public ResponseEntity<String> asyncSetCache(@RequestParam String key, @RequestParam String value) {
        try {
            CompletableFuture<Void> future = cacheService.setAsync(key, value, 30, TimeUnit.MINUTES);
            future.thenRun(() -> log.info("异步设置缓存完成: {} = {}", key, value));
            
            return ResponseEntity.ok("异步设置缓存已启动: " + key);
        } catch (Exception e) {
            log.error("异步设置缓存失败", e);
            return ResponseEntity.internalServerError().body("异步设置缓存失败: " + e.getMessage());
        }
    }

    /**
     * 测试批量缓存操作
     */
    @PostMapping("/batch-set")
    public ResponseEntity<String> batchSetCache(@RequestBody List<CacheData> dataList) {
        try {
            java.util.Map<String, String> data = new java.util.HashMap<>();
            for (CacheData cacheData : dataList) {
                data.put(cacheData.getKey(), cacheData.getValue());
            }
            
            cacheService.mset(data, 30, TimeUnit.MINUTES);
            return ResponseEntity.ok("批量设置缓存成功，数量: " + data.size());
        } catch (Exception e) {
            log.error("批量设置缓存失败", e);
            return ResponseEntity.internalServerError().body("批量设置缓存失败: " + e.getMessage());
        }
    }

    /**
     * 测试批量获取缓存
     */
    @PostMapping("/batch-get")
    public ResponseEntity<java.util.Map<String, String>> batchGetCache(@RequestBody List<String> keys) {
        try {
            java.util.Map<String, String> result = cacheService.mget(keys, String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("批量获取缓存失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 测试缓存统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Object>> getCacheStats() {
        try {
            java.util.Map<String, Object> stats = cacheService.getCacheStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取缓存统计信息失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 测试缓存性能
     */
    @GetMapping("/performance")
    public ResponseEntity<String> testPerformance() {
        try {
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
            
            String result = String.format("1000次缓存读取耗时: %dms, 平均每次: %.2fms", 
                endTime - startTime, (endTime - startTime) / 1000.0);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("性能测试失败", e);
            return ResponseEntity.internalServerError().body("性能测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试@Cacheable注解
     */
    @Cacheable(value = "test", key = "#id", unless = "#result == null")
    @GetMapping("/cached/{id}")
    public ResponseEntity<String> getCachedData(@PathVariable Long id) {
        log.info("从数据源获取数据: {}", id);
        // 模拟数据库查询
        String data = "缓存数据: " + id;
        return ResponseEntity.ok(data);
    }

    /**
     * 清空所有缓存
     */
    @DeleteMapping("/clear")
    public ResponseEntity<String> clearAllCache() {
        try {
            cacheService.clearAll();
            return ResponseEntity.ok("所有缓存已清空");
        } catch (Exception e) {
            log.error("清空缓存失败", e);
            return ResponseEntity.internalServerError().body("清空缓存失败: " + e.getMessage());
        }
    }

    /**
     * 缓存数据类
     */
    public static class CacheData {
        private String key;
        private String value;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}