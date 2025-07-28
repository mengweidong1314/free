# Java高可用高性能项目

本项目展示了Java高可用、高性能开发的最佳实践，包含多种技术栈和架构模式。

## 🚀 项目特性

### 高可用特性
- **负载均衡**: Nginx + Spring Cloud Gateway
- **服务发现**: Eureka/Consul
- **熔断降级**: Hystrix/Sentinel
- **限流**: Sentinel/RateLimiter
- **集群部署**: Docker + Kubernetes
- **监控告警**: Prometheus + Grafana

### 高性能特性
- **异步处理**: CompletableFuture + Reactor
- **缓存策略**: Redis + Caffeine
- **连接池**: HikariCP + Netty
- **JVM优化**: G1GC + 参数调优
- **数据库优化**: 读写分离 + 分库分表
- **消息队列**: RabbitMQ + Kafka

## 📁 项目结构

```
java-high-performance/
├── gateway/                 # API网关
├── user-service/           # 用户服务
├── order-service/          # 订单服务
├── payment-service/        # 支付服务
├── common/                 # 公共模块
├── config/                 # 配置文件
├── docker/                 # Docker配置
├── k8s/                    # Kubernetes配置
└── docs/                   # 文档
```

## 🛠️ 技术栈

- **框架**: Spring Boot 3.x, Spring Cloud 2023.x
- **网关**: Spring Cloud Gateway
- **服务发现**: Eureka/Consul
- **熔断**: Sentinel
- **缓存**: Redis, Caffeine
- **数据库**: MySQL 8.0, MyBatis-Plus
- **消息队列**: RabbitMQ, Kafka
- **监控**: Prometheus, Grafana
- **容器化**: Docker, Kubernetes
- **异步**: CompletableFuture, Project Reactor

## 🚀 快速开始

### 环境要求
- JDK 17+
- Maven 3.8+
- Docker & Docker Compose
- Kubernetes (可选)

### 启动步骤
1. 克隆项目
2. 启动基础设施 (Redis, MySQL, RabbitMQ)
3. 启动微服务
4. 访问监控面板

详细启动说明请参考各模块的README文件。