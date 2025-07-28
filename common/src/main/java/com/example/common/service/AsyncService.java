package com.example.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 高性能异步服务
 * 支持CompletableFuture、批量操作、超时控制
 */
@Slf4j
@Service
public class AsyncService {

    /**
     * 异步执行任务
     */
    @Async("commonTaskExecutor")
    public <T> CompletableFuture<T> executeAsync(Supplier<T> task) {
        try {
            T result = task.get();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("异步任务执行失败", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 异步执行无返回值任务
     */
    @Async("commonTaskExecutor")
    public CompletableFuture<Void> executeAsync(Runnable task) {
        try {
            task.run();
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("异步任务执行失败", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 批量异步执行任务
     */
    public <T> CompletableFuture<List<T>> executeBatchAsync(List<Supplier<T>> tasks) {
        List<CompletableFuture<T>> futures = tasks.stream()
                .map(this::executeAsync)
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * 带超时的异步执行
     */
    public <T> CompletableFuture<T> executeWithTimeout(Supplier<T> task, long timeout, TimeUnit unit) {
        CompletableFuture<T> future = executeAsync(task);
        return future.orTimeout(timeout, unit);
    }

    /**
     * 异步执行IO密集型任务
     */
    @Async("ioTaskExecutor")
    public <T> CompletableFuture<T> executeIOAsync(Supplier<T> task) {
        return executeAsync(task);
    }

    /**
     * 异步执行CPU密集型任务
     */
    @Async("cpuTaskExecutor")
    public <T> CompletableFuture<T> executeCPUAsync(Supplier<T> task) {
        return executeAsync(task);
    }

    /**
     * 异步流水线处理
     * 支持多个异步任务的链式调用
     */
    public <T, R> CompletableFuture<R> pipelineAsync(T input, 
                                                    List<Function<T, CompletableFuture<R>>> processors) {
        CompletableFuture<R> future = CompletableFuture.completedFuture(null);
        
        for (Function<T, CompletableFuture<R>> processor : processors) {
            future = future.thenCompose(result -> processor.apply(input));
        }
        
        return future;
    }

    /**
     * 异步聚合操作
     * 等待多个异步任务完成并聚合结果
     */
    public <T> CompletableFuture<List<T>> aggregateAsync(List<CompletableFuture<T>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * 异步重试机制
     */
    public <T> CompletableFuture<T> retryAsync(Supplier<T> task, int maxRetries, long delay, TimeUnit unit) {
        return retryAsync(task, maxRetries, delay, unit, 0);
    }

    private <T> CompletableFuture<T> retryAsync(Supplier<T> task, int maxRetries, long delay, TimeUnit unit, int currentRetry) {
        return executeAsync(task)
                .exceptionally(throwable -> {
                    if (currentRetry < maxRetries) {
                        log.warn("任务执行失败，准备重试 {}/{}", currentRetry + 1, maxRetries);
                        try {
                            Thread.sleep(unit.toMillis(delay));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        return retryAsync(task, maxRetries, delay, unit, currentRetry + 1).join();
                    } else {
                        log.error("任务重试{}次后仍然失败", maxRetries, throwable);
                        throw new RuntimeException(throwable);
                    }
                });
    }

    /**
     * 异步限流执行
     * 控制并发执行的任务数量
     */
    public <T> CompletableFuture<List<T>> executeWithConcurrencyLimit(
            List<Supplier<T>> tasks, int maxConcurrency) {
        
        List<CompletableFuture<T>> futures = tasks.stream()
                .collect(Collectors.groupingBy(task -> tasks.indexOf(task) % maxConcurrency))
                .values()
                .stream()
                .map(group -> group.stream()
                        .map(this::executeAsync)
                        .collect(Collectors.toList()))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        return aggregateAsync(futures);
    }

    /**
     * 异步缓存更新
     * 先更新缓存，再异步更新数据库
     */
    public <T> CompletableFuture<T> updateWithCache(T data, 
                                                   Function<T, T> cacheUpdater,
                                                   Function<T, T> dbUpdater) {
        // 先更新缓存
        T cachedData = cacheUpdater.apply(data);
        
        // 异步更新数据库
        return executeAsync(() -> dbUpdater.apply(cachedData))
                .thenApply(result -> cachedData);
    }

    /**
     * 异步事件发布
     * 支持事件驱动的异步处理
     */
    public <T> CompletableFuture<Void> publishEventAsync(T event, 
                                                        List<Function<T, CompletableFuture<Void>>> handlers) {
        List<CompletableFuture<Void>> futures = handlers.stream()
                .map(handler -> executeAsync(() -> handler.apply(event).join()))
                .collect(Collectors.toList());

        return aggregateAsync(futures).thenApply(results -> null);
    }
}