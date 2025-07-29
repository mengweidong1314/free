@Slf4j
@Component
public class AreaFreightUpdateCommandExecutor {

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

    // 缓存类目数据，避免重复查询
    private Map<Long, CategoryDTO> categoryCache;
    private Map<Long, List<CategoryDTO>> parentToChildrenCache;

    /**
     * 更新区域运费
     *
     * @param command 更新命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void execute(AreaFreightUpdateCommand command) {
        Long companyId = command.getCompanyId();

        // 获取版次号：QYYF前缀+yyMMdd日期+0001递增序列
        String newVersion = AreaPrefixEnum.PRICE_AREA_FREIGHT_DETAIL.getPrefix() + generateId();

        // 预加载类目数据到缓存
        preloadCategoryData(companyId);

        AreaVersionUpdateCommand areaVersionUpdateCommand = new AreaVersionUpdateCommand();
        BeanUtils.copyProperties(command, areaVersionUpdateCommand);
        areaVersionUpdateCommand.setVersion(newVersion);

        // 更新旧数据
        List<AreaFreightView> areaFreightViews = updateOldDataInBatches(companyId, newVersion);

        if (areaFreightViews.isEmpty()) {
            throw AreaFreightAssert.AREA_FREIGHT_DRAFT_NOT_EXIST.newException();
        }

        // 读取原始数据并转换为 AreaFreight 实体
        List<AreaFreight> allFreightList = fetchAndConvertDataOptimized(areaFreightViews, command);

        if (allFreightList.isEmpty()) {
            throw AreaFreightAssert.UPDATE_AREA_FREIGHT_FAILED.newException();
        }

        // 批量插入新数据，使用更大的批次大小
        saveNewDataInBatchesOptimized(allFreightList);

        // 创建区域版次
        areaVersionCreateExecutor.execute(areaVersionUpdateCommand, "Freight");
    }

    /**
     * 预加载类目数据到缓存
     */
    private void preloadCategoryData(Long companyId) {
        List<CategoryDTO> allCategories = categoryApi.getCategoryByCompanyId(companyId);
        
        // 构建类目缓存
        categoryCache = allCategories.stream()
                .filter(item -> StatusEnum.ENABLED.equals(item.getStatus()))
                .collect(Collectors.toMap(
                    CategoryDTO::getId, 
                    Function.identity(),
                    (existing, replacement) -> existing
                ));
        
        // 构建父子关系缓存
        parentToChildrenCache = allCategories.stream()
                .filter(item -> StatusEnum.ENABLED.equals(item.getStatus()))
                .collect(Collectors.groupingBy(
                    category -> category.getParentId() == null ? 0L : category.getParentId()
                ));
    }

    /**
     * 优化后的数据转换方法
     */
    private List<AreaFreight> fetchAndConvertDataOptimized(List<AreaFreightView> areaFreightViews, AreaFreightUpdateCommand command) {
        Map<String, AreaFreight> freightMap = new HashMap<>();
        Long companyId = command.getCompanyId();

        // 预计算所有已存在的三级类目 key
        Set<String> existingLeafCategoryKeys = precomputeExistingLeafKeys(areaFreightViews);

        // 使用流式处理，减少中间集合创建
        areaFreightViews.stream()
                .filter(view -> categoryCache.containsKey(view.getCategoryId()))
                .forEach(view -> {
                    CategoryDTO category = categoryCache.get(view.getCategoryId());
                    processCategoryView(category, view, command, freightMap, existingLeafCategoryKeys);
                });

        return new ArrayList<>(freightMap.values());
    }

    /**
     * 预计算已存在的三级类目 key
     */
    private Set<String> precomputeExistingLeafKeys(List<AreaFreightView> areaFreightViews) {
        return areaFreightViews.stream()
                .filter(view -> {
                    CategoryDTO category = categoryCache.get(view.getCategoryId());
                    return category != null && category.getLevel() == 3;
                })
                .map(view -> {
                    CategoryDTO category = categoryCache.get(view.getCategoryId());
                    return buildFreightKey(category, view);
                })
                .collect(Collectors.toSet());
    }

    /**
     * 处理单个类目视图
     */
    private void processCategoryView(CategoryDTO category, AreaFreightView view, 
                                   AreaFreightUpdateCommand command, 
                                   Map<String, AreaFreight> freightMap, 
                                   Set<String> existingLeafCategoryKeys) {
        
        // 展开为所有对应的三级类目
        List<CategoryDTO> leafCategories = expandToLeafCategoriesOptimized(category);

        for (CategoryDTO leafCategory : leafCategories) {
            String key = buildFreightKey(leafCategory, view);

            // 优化判断逻辑
            if (category.getLevel() < 3 && existingLeafCategoryKeys.contains(key)) {
                continue;
            }

            // 使用 computeIfAbsent 避免重复检查
            freightMap.computeIfAbsent(key, k -> createFreight(leafCategory, view, command));
        }
    }

    /**
     * 优化后的类目展开方法，使用缓存
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
     * 创建运费对象
     */
    private AreaFreight createFreight(CategoryDTO category, AreaFreightView view, AreaFreightUpdateCommand command) {
        AreaFreight freight = new AreaFreight();
        initializeFreight(freight, category, view, command);
        return freight;
    }

    /**
     * 初始化运费信息 - 优化版本
     */
    private void initializeFreight(AreaFreight freight, CategoryDTO category, AreaFreightView view, AreaFreightUpdateCommand command) {
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
    }

    /**
     * 构建 key - 使用 StringBuilder 优化字符串拼接
     */
    private String buildFreightKey(CategoryDTO category, AreaFreightView view) {
        StringBuilder keyBuilder = new StringBuilder();
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
    }

    /**
     * 批量更新旧数据 - 优化版本
     */
    private List<AreaFreightView> updateOldDataInBatches(Long companyId, String newVersion) {
        List<AreaFreightView> areaFreightViewList = areaFreightViewRepository.getAllByCompanyIdAndAreaFreightVersionIsNull(companyId);

        if (areaFreightViewList.isEmpty()) {
            throw AreaFreightAssert.UPDATE_AREA_FREIGHT_NOT_EXIST.newException();
        }

        // 批量设置版本号
        areaFreightViewList.forEach(item -> item.setAreaFreightVersion(newVersion));
        
        // 使用批量保存
        areaFreightViewRepository.saveAll(areaFreightViewList);
        return areaFreightViewList;
    }

    /**
     * 优化后的批量插入方法
     */
    private void saveNewDataInBatchesOptimized(List<AreaFreight> allFreightList) {
        // 使用更大的批次大小，减少数据库交互次数
        int batchSize = 1000;
        for (int i = 0; i < allFreightList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, allFreightList.size());
            List<AreaFreight> batch = allFreightList.subList(i, endIndex);
            areaFreightRepository.saveAllAndFlush(batch);
        }
    }

    /**
     * 版次号生成
     */
    public String generateId() {
        return idApi.periodIncrement(AreaPrefixEnum.PRICE_AREA_FREIGHT_DETAIL_TABLE.getPrefix());
    }

    // 清理缓存的方法，在事务结束后调用
    private void clearCache() {
        categoryCache = null;
        parentToChildrenCache = null;
    }
}