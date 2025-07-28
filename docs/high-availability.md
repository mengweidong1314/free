# 高可用架构设计

## 1. 架构概述

### 整体架构
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Load Balancer │    │   Load Balancer │    │   Load Balancer │
│   (Nginx)       │    │   (Nginx)       │    │   (Nginx)       │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
┌─────────▼───────┐    ┌─────────▼───────┐    ┌─────────▼───────┐
│  Gateway        │    │  Gateway        │    │  Gateway        │
│  (Instance 1)   │    │  (Instance 2)   │    │  (Instance 3)   │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
┌─────────▼───────┐    ┌─────────▼───────┐    ┌─────────▼───────┐
│  User Service   │    │  Order Service  │    │ Payment Service │
│  (Instance 1)   │    │  (Instance 1)   │    │  (Instance 1)   │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
┌─────────▼───────┐    ┌─────────▼───────┐    ┌─────────▼───────┐
│  User Service   │    │  Order Service  │    │ Payment Service │
│  (Instance 2)   │    │  (Instance 2)   │    │  (Instance 2)   │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
┌─────────▼──────────────────────▼──────────────────────▼───────┐
│                    Service Registry (Eureka)                  │
└───────────────────────────────────────────────────────────────┘
```

## 2. 高可用组件

### 2.1 负载均衡
- **Nginx**: 四层和七层负载均衡
- **Spring Cloud Gateway**: 微服务网关负载均衡
- **客户端负载均衡**: Ribbon + Feign

### 2.2 服务发现
- **Eureka**: 服务注册与发现
- **Consul**: 服务注册与发现（可选）
- **Nacos**: 配置中心和服务发现（可选）

### 2.3 熔断降级
- **Sentinel**: 流量控制、熔断降级
- **Hystrix**: 熔断器（已停止维护）

### 2.4 监控告警
- **Prometheus**: 指标收集
- **Grafana**: 可视化监控
- **AlertManager**: 告警管理

## 3. 高可用策略

### 3.1 服务高可用
```yaml
# 服务实例配置
spring:
  cloud:
    loadbalancer:
      ribbon:
        enabled: true
        ConnectTimeout: 1000
        ReadTimeout: 3000
        MaxAutoRetries: 1
        MaxAutoRetriesNextServer: 1
        OkToRetryOnAllOperations: false
```

### 3.2 数据库高可用
```yaml
# 主从复制配置
spring:
  datasource:
    master:
      url: jdbc:mysql://master:3306/db
      username: root
      password: password
    slave:
      url: jdbc:mysql://slave:3306/db
      username: root
      password: password
```

### 3.3 缓存高可用
```yaml
# Redis集群配置
spring:
  redis:
    cluster:
      nodes:
        - 192.168.1.10:6379
        - 192.168.1.11:6379
        - 192.168.1.12:6379
      max-redirects: 3
```

## 4. 故障恢复机制

### 4.1 自动故障转移
```java
@Configuration
public class LoadBalancerConfig {
    
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2);
        backOffPolicy.setMaxInterval(10000);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }
}
```

### 4.2 熔断器配置
```java
@HystrixCommand(
    fallbackMethod = "fallbackMethod",
    commandProperties = {
        @HystrixProperty(name = "circuitBreaker.requestVolumeThreshold", value = "10"),
        @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", value = "50"),
        @HystrixProperty(name = "circuitBreaker.sleepWindowInMilliseconds", value = "5000")
    }
)
public String callExternalService() {
    // 调用外部服务
    return restTemplate.getForObject("http://external-service/api", String.class);
}

public String fallbackMethod() {
    return "Service is temporarily unavailable";
}
```

## 5. 监控告警

### 5.1 健康检查
```java
@Component
public class HealthIndicator implements org.springframework.boot.actuator.health.HealthIndicator {
    
    @Override
    public Health health() {
        try {
            // 检查数据库连接
            checkDatabaseConnection();
            // 检查Redis连接
            checkRedisConnection();
            // 检查外部服务
            checkExternalService();
            
            return Health.up()
                    .withDetail("database", "UP")
                    .withDetail("redis", "UP")
                    .withDetail("external-service", "UP")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
```

### 5.2 告警规则
```yaml
# Prometheus告警规则
groups:
  - name: high-availability
    rules:
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.instance }} is down"
          
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          
      - alert: HighResponseTime
        expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m])) > 1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High response time detected"
```

## 6. 部署策略

### 6.1 蓝绿部署
```yaml
# Kubernetes蓝绿部署
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service-blue
spec:
  replicas: 3
  selector:
    matchLabels:
      app: user-service
      version: blue
  template:
    metadata:
      labels:
        app: user-service
        version: blue
    spec:
      containers:
      - name: user-service
        image: user-service:blue
        ports:
        - containerPort: 8081
```

### 6.2 滚动更新
```yaml
# 滚动更新配置
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
```

## 7. 灾难恢复

### 7.1 数据备份
```bash
#!/bin/bash
# 数据库备份脚本
mysqldump -h localhost -u root -p high_performance > backup_$(date +%Y%m%d_%H%M%S).sql

# Redis备份脚本
redis-cli BGSAVE
```

### 7.2 跨区域部署
```yaml
# 多区域部署配置
spring:
  cloud:
    kubernetes:
      discovery:
        all-namespaces: true
        cluster-domain: cluster.local
      config:
        sources:
          - namespace: default
            name: config-map
```

## 8. 性能优化

### 8.1 连接池优化
```yaml
# 数据库连接池
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 8.2 缓存优化
```yaml
# Redis缓存配置
spring:
  cache:
    redis:
      time-to-live: 600000
      cache-null-values: false
      use-key-prefix: true
```

## 9. 安全措施

### 9.1 网络安全
- 使用HTTPS/TLS加密
- 实施API网关安全策略
- 配置防火墙规则

### 9.2 应用安全
- 输入验证和过滤
- SQL注入防护
- XSS攻击防护
- CSRF防护

## 10. 测试策略

### 10.1 故障注入测试
```java
@Test
public void testCircuitBreaker() {
    // 模拟服务故障
    when(externalService.call()).thenThrow(new RuntimeException("Service unavailable"));
    
    // 验证熔断器行为
    String result = userService.callExternalService();
    assertEquals("Service is temporarily unavailable", result);
}
```

### 10.2 压力测试
```java
@Test
public void testLoadBalancing() {
    // 模拟高并发请求
    List<CompletableFuture<String>> futures = IntStream.range(0, 100)
        .mapToObj(i -> CompletableFuture.supplyAsync(() -> 
            restTemplate.getForObject("http://user-service/api/users/1", String.class)))
        .collect(Collectors.toList());
    
    // 验证负载均衡效果
    futures.forEach(future -> assertNotNull(future.join()));
}
```