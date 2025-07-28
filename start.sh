#!/bin/bash

# Java高可用高性能项目启动脚本

echo "🚀 启动Java高可用高性能项目..."

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker未运行，请先启动Docker"
    exit 1
fi

# 检查Docker Compose是否安装
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose未安装，请先安装Docker Compose"
    exit 1
fi

# 创建必要的目录
echo "📁 创建必要的目录..."
mkdir -p docker/mysql
mkdir -p docker/redis
mkdir -p docker/rabbitmq
mkdir -p docker/eureka
mkdir -p docker/sentinel
mkdir -p docker/prometheus
mkdir -p docker/grafana
mkdir -p docker/nginx

# 启动基础设施服务
echo "🔧 启动基础设施服务..."
cd docker
docker-compose up -d mysql redis rabbitmq

# 等待数据库启动
echo "⏳ 等待数据库启动..."
sleep 30

# 检查数据库连接
echo "🔍 检查数据库连接..."
until docker-compose exec mysql mysqladmin ping -h"localhost" --silent; do
    echo "等待MySQL启动..."
    sleep 5
done

# 启动监控服务
echo "📊 启动监控服务..."
docker-compose up -d prometheus grafana

# 启动服务注册中心
echo "🏢 启动服务注册中心..."
docker-compose up -d eureka

# 等待Eureka启动
echo "⏳ 等待Eureka启动..."
sleep 20

# 启动微服务
echo "🔄 启动微服务..."
docker-compose up -d user-service order-service payment-service

# 启动负载均衡器
echo "⚖️ 启动负载均衡器..."
docker-compose up -d nginx

# 启动Sentinel控制台
echo "🛡️ 启动Sentinel控制台..."
docker-compose up -d sentinel

echo "✅ 所有服务启动完成！"
echo ""
echo "📋 服务访问地址："
echo "  - 用户服务: http://localhost:8081"
echo "  - 订单服务: http://localhost:8082"
echo "  - 支付服务: http://localhost:8083"
echo "  - Eureka控制台: http://localhost:8761"
echo "  - Sentinel控制台: http://localhost:8080"
echo "  - Prometheus: http://localhost:9090"
echo "  - Grafana: http://localhost:3000 (admin/admin)"
echo "  - RabbitMQ管理: http://localhost:15672 (admin/admin123)"
echo "  - Nginx: http://localhost:80"
echo ""
echo "🔧 常用命令："
echo "  - 查看服务状态: docker-compose ps"
echo "  - 查看服务日志: docker-compose logs -f [service-name]"
echo "  - 停止所有服务: docker-compose down"
echo "  - 重启服务: docker-compose restart [service-name]"
echo ""
echo "🎯 性能测试："
echo "  - 压力测试: wrk -t12 -c400 -d30s http://localhost:8081/api/users/1"
echo "  - 健康检查: curl http://localhost:8081/actuator/health"
echo "  - 监控指标: curl http://localhost:8081/actuator/metrics"