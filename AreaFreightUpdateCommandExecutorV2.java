import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class AreaFreightUpdateCommandExecutorV2 {

    @Resource
    private AreaFreightRepository areaFreightRepository;

    @Resource
    private AreaFreightViewRepository areaFreightViewRepository;

    @Resource
    private AreaVersionCreateExecutor areaVersionCreateExecutor;

    @Resource
    private IdApi idApi;

    @Resource
    private CategoryApi categoryApi;

    @Resource
    private AreaFreightConfig config;

    // 使用 ConcurrentHashMap 提高并发性能
    private final Map<Long, CategoryDTO> categoryCache = new ConcurrentHashMap<>();
    private final Map<Long, List<CategoryDTO>> parentToChildrenCache = new ConcurrentHashMap<>();
    
    // 缓存键的预计算
    private final Map<String, String> keyCache = new ConcurrentHashMap<>();

    /**
     * 更新区域运费 - 高性能版本
     */
    @Transactional(rollbackFor = Exception.class)
    public void execute(AreaFreightUpdateCommand command) {
        Long companyId = command.getCompanyId();
        String newVersion = AreaPrefixEnum.PRICE_AREA_FREIGHT_DETAIL.getPrefix() + generateId();

        try {
            // 预加载类目数据
            preloadCategoryData(companyId);

            AreaVersionUpdateCommand areaVersionUpdateCommand = new AreaVersionUpdateCommand();
            BeanUtils.copyProperties(command, areaVersionUpdateCommand);
            areaVersionUpdateCommand.setVersion(newVersion);

            // 并行处理数据更新和转换
            CompletableFuture<List<AreaFreightView>> updateFuture = 
                CompletableFuture.supplyAsync(() -> updateOldDataInBatches(companyId, newVersion));

            List<AreaFreightView> areaFreightViews;
            try {
                areaFreightViews = updateFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to update old data", e);
                throw new RuntimeException("Failed to update old data", e);
            }
            
            if (areaFreightViews.isEmpty()) {
                throw AreaFreightAssert.AREA_FREIGHT_DRAFT_NOT_EXIST.newException();
            }

            // 并行处理数据转换
            List<AreaFreight> allFreightList = fetchAndConvertDataParallel(areaFreightViews, command);

            if (allFreightList.isEmpty()) {
                throw AreaFreightAssert.UPDATE_AREA_FREIGHT_FAILED.newException();
            }

            // 并行批量插入
            saveNewDataParallel(allFreightList);

            // 创建区域版次
            areaVersionCreateExecutor.execute(areaVersionUpdateCommand, "Freight");
            
        } finally {
            // 清理缓存
            clearCache();
        }
    }

    /**
     * 预加载类目数据到缓存
     */
    private void preloadCategoryData(Long companyId) {
        List<CategoryDTO> allCategories = categoryApi.getCategoryByCompanyId(companyId);
        
        // 并行构建缓存
        CompletableFuture<Map<Long, CategoryDTO>> categoryCacheFuture = 
            CompletableFuture.supplyAsync(() -> 
                allCategories.stream()
                    .filter(item -> StatusEnum.ENABLED.equals(item.getStatus()))
                    .collect(Collectors.toMap(
                        CategoryDTO::getId, 
                        Function.identity(),
                        (existing, replacement) -> existing
                    ))
            );

        CompletableFuture<Map<Long, List<CategoryDTO>>> parentCacheFuture = 
            CompletableFuture.supplyAsync(() -> 
                allCategories.stream()
                    .filter(item -> StatusEnum.ENABLED.equals(item.getStatus()))
                    .collect(Collectors.groupingBy(
                        category -> category.getParentId() == null ? 0L : category.getParentId()
                    ))
            );

        try {
            categoryCache.putAll(categoryCacheFuture.get());
            parentToChildrenCache.putAll(parentCacheFuture.get());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to preload category data", e);
            throw new RuntimeException("Failed to preload category data", e);
        }
    }

    /**
     * 并行数据转换
     */
    private List<AreaFreight> fetchAndConvertDataParallel(List<AreaFreightView> areaFreightViews, 
                                                         AreaFreightUpdateCommand command) {
        // 预计算已存在的三级类目 key
        Set<String> existingLeafCategoryKeys = precomputeExistingLeafKeysParallel(areaFreightViews);

        // 使用并行流处理数据
        return areaFreightViews.parallelStream()
                .filter(view -> categoryCache.containsKey(view.getCategoryId()))
                .flatMap(view -> {
                    CategoryDTO category = categoryCache.get(view.getCategoryId());
                    return processCategoryViewParallel(category, view, command, existingLeafCategoryKeys);
                })
                .collect(Collectors.toList());
    }

    /**
     * 并行预计算已存在的三级类目 key
     */
    private Set<String> precomputeExistingLeafCategoryKeysParallel(List<AreaFreightView> areaFreightViews) {
        return areaFreightViews.parallelStream()
                .filter(view -> {
                    CategoryDTO category = categoryCache.get(view.getCategoryId());
                    return category != null && category.getLevel() == 3;
                })
                .map(view -> {
                    CategoryDTO category = categoryCache.get(view.getCategoryId());
                    return buildFreightKeyOptimized(category, view);
                })
                .collect(Collectors.toSet());
    }

    /**
     * 并行处理单个类目视图
     */
    private Stream<AreaFreight> processCategoryViewParallel(CategoryDTO category, AreaFreightView view,
                                                           AreaFreightUpdateCommand command,
                                                           Set<String> existingLeafCategoryKeys) {
        
        List<CategoryDTO> leafCategories = expandToLeafCategoriesOptimized(category);

        return leafCategories.stream()
                .filter(leafCategory -> {
                    String key = buildFreightKeyOptimized(leafCategory, view);
                    return !(category.getLevel() < 3 && existingLeafCategoryKeys.contains(key));
                })
                .map(leafCategory -> createFreightOptimized(leafCategory, view, command));
    }

    /**
     * 优化后的类目展开方法
     */
    private List<CategoryDTO> expandToLeafCategoriesOptimized(CategoryDTO category) {
        if (category.getLevel() == 3) {
            return Collections.singletonList(category);
        }

        List<CategoryDTO> result = new ArrayList<>();
        List<CategoryDTO> children = parentToChildrenCache.getOrDefault(category.getId(), Collections.emptyList());
        
        for (CategoryDTO child : children) {
            result.addAll(expandToLeafCategoriesOptimized(child));
        }
        
        return result;
    }

    /**
     * 优化的运费对象创建
     */
    private AreaFreight createFreightOptimized(CategoryDTO category, AreaFreightView view, AreaFreightUpdateCommand command) {
        AreaFreight freight = new AreaFreight();
        
        // 使用批量设置减少方法调用
        freight.setId(null);
        freight.setCompanyId(command.getCompanyId());
        freight.setShopId(view.getShopId());
        freight.setShopName(view.getShopName());
        freight.setAreaFreightVersion(view.getAreaFreightVersion());
        freight.setCategoryId(category.getId());
        freight.setCategoryName(category.getName());

        // 设置区域信息
        freight.setAreaId(view.getAreaId());
        freight.setAreaName(view.getAreaName());
        freight.setProvinceCode(view.getProvinceCode());
        freight.setProvinceName(view.getProvinceName());
        freight.setCityCode(view.getCityCode());
        freight.setCityName(view.getCityName());
        freight.setCountyCode(view.getCountyCode());
        freight.setCountyName(view.getCountyName());

        // 初始化金额字段
        freight.setTruckTaxExclusiveFreight(view.getTruckTaxExclusiveFreight());
        freight.setTruckFreight(view.getTruckFreight());
        freight.setTrainOpenFreight(view.getTrainOpenFreight());
        freight.setTrainContainerFreight(view.getTrainContainerFreight());

        // 设置创建人信息
        freight.setCreatedUserId(command.getCreatedUserId());
        freight.setCreatedName(command.getCreatedName());
        
        return freight;
    }

    /**
     * 优化的 key 构建 - 使用缓存
     */
    private String buildFreightKeyOptimized(CategoryDTO category, AreaFreightView view) {
        String cacheKey = category.getId() + ":" + view.getAreaId() + ":" + view.getProvinceCode() + ":" + view.getCityCode() + ":" + view.getCountyCode();
        
        return keyCache.computeIfAbsent(cacheKey, k -> {
            StringBuilder keyBuilder = new StringBuilder(200); // 预分配容量
            keyBuilder.append(category.getId()).append(":")
                     .append(category.getName()).append(":")
                     .append(view.getAreaId()).append(":")
                     .append(view.getAreaName()).append(":")
                     .append(view.getProvinceCode()).append(":")
                     .append(view.getProvinceName()).append(":")
                     .append(view.getCityCode()).append(":")
                     .append(view.getCityName()).append(":")
                     .append(view.getCountyCode()).append(":")
                     .append(view.getCountyName());
            return keyBuilder.toString();
        });
    }

    /**
     * 批量更新旧数据
     */
    private List<AreaFreightView> updateOldDataInBatches(Long companyId, String newVersion) {
        List<AreaFreightView> areaFreightViewList = areaFreightViewRepository.getAllByCompanyIdAndAreaFreightVersionIsNull(companyId);

        if (areaFreightViewList.isEmpty()) {
            throw AreaFreightAssert.UPDATE_AREA_FREIGHT_NOT_EXIST.newException();
        }

        // 并行设置版本号
        areaFreightViewList.parallelStream().forEach(item -> item.setAreaFreightVersion(newVersion));
        
        // 批量保存
        areaFreightViewRepository.saveAll(areaFreightViewList);
        return areaFreightViewList;
    }

    /**
     * 并行批量插入
     */
    private void saveNewDataParallel(List<AreaFreight> allFreightList) {
        int batchSize = config.getBatchSize();
        int maxConcurrency = config.getMaxConcurrency();
        
        // 分批处理
        List<List<AreaFreight>> batches = new ArrayList<>();
        for (int i = 0; i < allFreightList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, allFreightList.size());
            batches.add(allFreightList.subList(i, endIndex));
        }

        // 并行处理批次
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);
        try {
            List<CompletableFuture<Void>> futures = batches.stream()
                    .map(batch -> CompletableFuture.runAsync(() -> {
                        areaFreightRepository.saveAllAndFlush(batch);
                    }, executor))
                    .collect(Collectors.toList());

            // 等待所有批次完成
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to save data in parallel", e);
                throw new RuntimeException("Failed to save data in parallel", e);
            }
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 版次号生成
     */
    public String generateId() {
        return idApi.periodIncrement(AreaPrefixEnum.PRICE_AREA_FREIGHT_DETAIL_TABLE.getPrefix());
    }

    /**
     * 清理缓存
     */
    private void clearCache() {
        categoryCache.clear();
        parentToChildrenCache.clear();
        keyCache.clear();
    }
}