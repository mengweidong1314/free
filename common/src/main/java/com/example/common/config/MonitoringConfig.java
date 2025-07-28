package com.example.common.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 高可用监控配置
 * 支持Prometheus、Micrometer等监控指标
 */
@Configuration
public class MonitoringConfig {

    /**
     * 配置TimedAspect用于方法执行时间监控
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * 配置MeterRegistry
     * 在生产环境中，通常会使用PrometheusMeterRegistry
     */
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}