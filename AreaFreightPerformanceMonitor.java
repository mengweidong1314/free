@Slf4j
@Component
public class AreaFreightPerformanceMonitor {

    private static final String METRIC_PREFIX = "area_freight_update";
    
    // 性能指标收集
    private final Map<String, Long> operationTimings = new ConcurrentHashMap<>();
    private final AtomicLong totalRecordsProcessed = new AtomicLong(0);
    private final AtomicLong totalBatchOperations = new AtomicLong(0);

    /**
     * 记录操作开始时间
     */
    public void startOperation(String operationName) {
        operationTimings.put(operationName + "_start", System.currentTimeMillis());
    }

    /**
     * 记录操作结束时间并计算耗时
     */
    public long endOperation(String operationName) {
        Long startTime = operationTimings.remove(operationName + "_start");
        if (startTime == null) {
            log.warn("No start time found for operation: {}", operationName);
            return 0;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Operation {} completed in {} ms", operationName, duration);
        
        // 记录性能指标
        recordMetric(operationName, duration);
        
        return duration;
    }

    /**
     * 记录性能指标
     */
    private void recordMetric(String operationName, long duration) {
        String metricName = METRIC_PREFIX + "." + operationName;
        log.info("Performance Metric - {}: {} ms", metricName, duration);
        
        // 这里可以集成到监控系统，如 Prometheus、Micrometer 等
        // Metrics.counter(metricName).increment();
        // Metrics.timer(metricName).record(duration, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录处理的记录数
     */
    public void recordRecordsProcessed(long count) {
        totalRecordsProcessed.addAndGet(count);
        log.info("Total records processed: {}", totalRecordsProcessed.get());
    }

    /**
     * 记录批量操作数
     */
    public void recordBatchOperation() {
        totalBatchOperations.incrementAndGet();
    }

    /**
     * 获取性能报告
     */
    public PerformanceReport generateReport() {
        return PerformanceReport.builder()
                .totalRecordsProcessed(totalRecordsProcessed.get())
                .totalBatchOperations(totalBatchOperations.get())
                .averageRecordsPerBatch(totalRecordsProcessed.get() / Math.max(totalBatchOperations.get(), 1))
                .build();
    }

    /**
     * 监控内存使用情况
     */
    public void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        log.info("Memory Usage - Used: {} MB, Free: {} MB, Total: {} MB, Max: {} MB",
                usedMemory / 1024 / 1024,
                freeMemory / 1024 / 1024,
                totalMemory / 1024 / 1024,
                maxMemory / 1024 / 1024);
    }

    /**
     * 性能报告
     */
    @Data
    @Builder
    public static class PerformanceReport {
        private long totalRecordsProcessed;
        private long totalBatchOperations;
        private double averageRecordsPerBatch;
        private Map<String, Long> operationTimings;
        
        @Override
        public String toString() {
            return String.format(
                "Performance Report - Records: %d, Batches: %d, Avg per batch: %.2f",
                totalRecordsProcessed, totalBatchOperations, averageRecordsPerBatch
            );
        }
    }

    /**
     * 批量大小优化建议
     */
    public int suggestOptimalBatchSize(long totalRecords, long avgProcessingTimeMs) {
        // 基于处理时间和记录数计算最优批次大小
        if (avgProcessingTimeMs < 100) {
            return 2000; // 快速处理，使用更大的批次
        } else if (avgProcessingTimeMs < 500) {
            return 1000; // 中等速度，使用标准批次
        } else {
            return 500; // 慢速处理，使用较小的批次
        }
    }

    /**
     * 并发度优化建议
     */
    public int suggestOptimalConcurrency(int availableCores, long avgProcessingTimeMs) {
        // 基于CPU核心数和处理时间计算最优并发度
        int suggestedConcurrency = Math.min(availableCores * 2, 8);
        
        if (avgProcessingTimeMs > 1000) {
            suggestedConcurrency = Math.max(suggestedConcurrency / 2, 2);
        }
        
        return suggestedConcurrency;
    }
}