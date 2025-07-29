@Configuration
@ConfigurationProperties(prefix = "area.freight")
@Data
public class AreaFreightConfig {
    
    /**
     * 批量插入的批次大小
     */
    private int batchSize = 1000;
    
    /**
     * 是否启用缓存
     */
    private boolean enableCache = true;
    
    /**
     * 缓存过期时间（秒）
     */
    private int cacheExpireSeconds = 300;
    
    /**
     * 最大并发处理线程数
     */
    private int maxConcurrency = 4;
}