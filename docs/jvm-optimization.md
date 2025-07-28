# JVM性能优化配置

## 1. 垃圾收集器选择

### G1GC (推荐)
```bash
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1MixedGCCountTarget=8
-XX:+G1UseAdaptiveIHOP
-XX:G1MixedGCLiveThresholdPercent=85
```

### ZGC (低延迟场景)
```bash
-XX:+UseZGC
-XX:+UnlockExperimentalVMOptions
-XX:+UseTransparentHugePages
```

## 2. 内存配置

### 堆内存设置
```bash
-Xms4g                    # 初始堆大小
-Xmx4g                    # 最大堆大小
-XX:NewRatio=3            # 新生代与老年代比例
-XX:SurvivorRatio=8       # Eden与Survivor比例
```

### 元空间设置
```bash
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m
```

## 3. 线程配置

### 线程池优化
```bash
-XX:ThreadStackSize=256k
-XX:+UseThreadPriorities
-XX:ThreadPriorityPolicy=1
```

## 4. 性能监控

### JMX监控
```bash
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9999
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
```

### GC日志
```bash
-Xlog:gc*:file=gc.log:time,uptime:filecount=5,filesize=100M
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-XX:+PrintGCDateStamps
```

## 5. 容器化优化

### Docker环境
```bash
-XX:+UnlockExperimentalVMOptions
-XX:+UseCGroupMemoryLimitForHeap
-XX:MaxRAMPercentage=75.0
-XX:InitialRAMPercentage=50.0
```

## 6. 完整配置示例

### 生产环境配置
```bash
JAVA_OPTS="
-Xms4g
-Xmx4g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1MixedGCCountTarget=8
-XX:+G1UseAdaptiveIHOP
-XX:G1MixedGCLiveThresholdPercent=85
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m
-XX:ThreadStackSize=256k
-XX:+UseThreadPriorities
-XX:ThreadPriorityPolicy=1
-XX:+UnlockExperimentalVMOptions
-XX:+UseCGroupMemoryLimitForHeap
-XX:MaxRAMPercentage=75.0
-XX:InitialRAMPercentage=50.0
-Xlog:gc*:file=gc.log:time,uptime:filecount=5,filesize=100M
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9999
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
"
```

### 开发环境配置
```bash
JAVA_OPTS="
-Xms1g
-Xmx2g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:MetaspaceSize=128m
-XX:MaxMetaspaceSize=256m
-Xlog:gc*:stdout:time,uptime
"
```

## 7. 性能调优建议

### 1. 内存调优
- 根据应用实际使用情况调整堆大小
- 监控GC频率和停顿时间
- 避免频繁的Full GC

### 2. GC调优
- 选择合适的垃圾收集器
- 调整GC参数减少停顿时间
- 监控GC日志分析性能瓶颈

### 3. 线程调优
- 合理设置线程池大小
- 避免线程泄漏
- 监控线程状态

### 4. 监控告警
- 设置内存使用率告警
- 监控GC频率和停顿时间
- 设置线程数告警

## 8. 性能测试

### 压力测试
```bash
# 使用JMeter进行压力测试
jmeter -n -t test-plan.jmx -l results.jtl

# 使用wrk进行HTTP压力测试
wrk -t12 -c400 -d30s http://localhost:8081/api/users/1
```

### 性能分析
```bash
# 使用JProfiler分析性能
# 使用VisualVM监控JVM
# 使用Arthas进行在线诊断
```