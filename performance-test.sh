#!/bin/bash

# Java高可用高性能项目性能测试脚本

echo "🎯 开始性能测试..."

# 检查wrk是否安装
if ! command -v wrk &> /dev/null; then
    echo "❌ wrk未安装，请先安装wrk"
    echo "Ubuntu/Debian: sudo apt-get install wrk"
    echo "CentOS/RHEL: sudo yum install wrk"
    echo "macOS: brew install wrk"
    exit 1
fi

# 测试配置
BASE_URL="http://localhost:8081"
TEST_DURATION="30s"
THREADS=12
CONNECTIONS=400

echo "📊 测试配置："
echo "  - 基础URL: $BASE_URL"
echo "  - 测试时长: $TEST_DURATION"
echo "  - 线程数: $THREADS"
echo "  - 连接数: $CONNECTIONS"
echo ""

# 1. 健康检查测试
echo "🔍 1. 健康检查测试..."
curl -s "$BASE_URL/actuator/health" | jq '.' 2>/dev/null || echo "健康检查失败"

# 2. 单用户查询测试
echo ""
echo "👤 2. 单用户查询测试..."
wrk -t$THREADS -c$CONNECTIONS -d$TEST_DURATION --latency "$BASE_URL/api/users/1"

# 3. 异步用户查询测试
echo ""
echo "⚡ 3. 异步用户查询测试..."
wrk -t$THREADS -c$CONNECTIONS -d$TEST_DURATION --latency "$BASE_URL/api/users/1/async"

# 4. 批量用户查询测试
echo ""
echo "📦 4. 批量用户查询测试..."
wrk -t$THREADS -c$CONNECTIONS -d$TEST_DURATION --latency -s batch_test.lua "$BASE_URL/api/users/batch"

# 5. 用户搜索测试
echo ""
echo "🔍 5. 用户搜索测试..."
wrk -t$THREADS -c$CONNECTIONS -d$TEST_DURATION --latency "$BASE_URL/api/users/search?keyword=test"

# 6. 用户创建测试
echo ""
echo "➕ 6. 用户创建测试..."
wrk -t$THREADS -c$CONNECTIONS -d$TEST_DURATION --latency -s create_test.lua "$BASE_URL/api/users"

# 7. 缓存性能测试
echo ""
echo "💾 7. 缓存性能测试..."
echo "第一次访问（未缓存）:"
wrk -t$THREADS -c$CONNECTIONS -d10s --latency "$BASE_URL/api/users/1"
echo ""
echo "第二次访问（已缓存）:"
wrk -t$THREADS -c$CONNECTIONS -d10s --latency "$BASE_URL/api/users/1"

# 8. 并发测试
echo ""
echo "🔄 8. 并发测试..."
for i in {1..5}; do
    echo "并发测试 $i/5:"
    wrk -t$THREADS -c$CONNECTIONS -d10s --latency "$BASE_URL/api/users/1"
    echo ""
done

# 9. 监控指标检查
echo ""
echo "📈 9. 监控指标检查..."
echo "JVM指标:"
curl -s "$BASE_URL/actuator/metrics/jvm.memory.used" | jq '.' 2>/dev/null || echo "JVM指标获取失败"

echo ""
echo "HTTP指标:"
curl -s "$BASE_URL/actuator/metrics/http.server.requests" | jq '.' 2>/dev/null || echo "HTTP指标获取失败"

echo ""
echo "缓存指标:"
curl -s "$BASE_URL/actuator/metrics/cache.gets" | jq '.' 2>/dev/null || echo "缓存指标获取失败"

# 10. 生成测试报告
echo ""
echo "📋 10. 生成测试报告..."
REPORT_FILE="performance-test-report-$(date +%Y%m%d_%H%M%S).txt"

cat > "$REPORT_FILE" << EOF
Java高可用高性能项目性能测试报告
生成时间: $(date)
测试配置:
- 基础URL: $BASE_URL
- 测试时长: $TEST_DURATION
- 线程数: $THREADS
- 连接数: $CONNECTIONS

测试结果:
$(wrk -t$THREADS -c$CONNECTIONS -d$TEST_DURATION --latency "$BASE_URL/api/users/1" 2>&1)

系统信息:
$(uname -a)
$(java -version 2>&1)
$(docker --version 2>&1)
EOF

echo "✅ 测试完成！报告已保存到: $REPORT_FILE"

# 11. 性能建议
echo ""
echo "💡 性能优化建议："
echo "  1. 如果QPS较低，考虑增加缓存"
echo "  2. 如果响应时间较长，检查数据库查询"
echo "  3. 如果错误率较高，检查熔断器配置"
echo "  4. 如果内存使用过高，调整JVM参数"
echo "  5. 如果CPU使用过高，优化算法或增加实例"