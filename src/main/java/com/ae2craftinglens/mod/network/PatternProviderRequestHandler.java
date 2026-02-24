package com.ae2craftinglens.mod.network;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PatternProviderRequestHandler {
    
    public static void handle(RequestPatternProvidersPacket packet, IPayloadContext context) {
        AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Server received pattern provider request ===");
        
        if (!(context.player() instanceof ServerPlayer player)) {
            AE2CraftingLens.LOGGER.warn("Player is not a ServerPlayer");
            return;
        }
        
        Object targetKey = packet.what();
        
        if (targetKey == null) {
            AE2CraftingLens.LOGGER.info("Received null AEKey, looking for all active pattern providers");
        } else {
            AE2CraftingLens.LOGGER.info("Looking for pattern providers for: {}", targetKey);
        }
        
        if (player.containerMenu == null) {
            AE2CraftingLens.LOGGER.info("No container menu open");
            return;
        }
        
        String containerClassName = player.containerMenu.getClass().getName();
        AE2CraftingLens.LOGGER.info("Container class: {}", containerClassName);
        
        // 检查是否是合成相关的容器，包括无线终端的情况
        if (!containerClassName.contains("Crafting") && !containerClassName.contains("crafting")) {
            AE2CraftingLens.LOGGER.info("Not a crafting menu");
            return;
        }
        
        AE2CraftingLens.LOGGER.info("Crafting menu detected");
        
        try {
            Object grid = null;
            
            // 方法1: 尝试从 CraftingStatusMenu 获取 grid
            if (containerClassName.contains("CraftingStatusMenu")) {
                AE2CraftingLens.LOGGER.info("Attempting to get grid from CraftingStatusMenu");
                grid = findGridFromCraftingStatusMenu(player.containerMenu);
            }
            
            // 方法2: 尝试找到 CraftingCPUCluster 然后获取 grid
            if (grid == null) {
                AE2CraftingLens.LOGGER.info("Attempting to find CraftingCPUCluster");
                Object cluster = findFieldByTypeName(player.containerMenu, "CraftingCPUCluster");
                if (cluster != null) {
                    AE2CraftingLens.LOGGER.info("Found CraftingCPUCluster: {}", cluster);
                    grid = invokeMethod(cluster, "getGrid", Object.class);
                }
            }
            
            // 方法3: 尝试直接从 menu 获取 grid
            if (grid == null) {
                AE2CraftingLens.LOGGER.info("Attempting to get grid directly from menu");
                grid = invokeMethod(player.containerMenu, "getGrid", Object.class);
            }
            
            // 方法4: 尝试找到包含 "Grid" 的字段
            if (grid == null) {
                AE2CraftingLens.LOGGER.info("Attempting to find grid field");
                grid = findFieldByTypeName(player.containerMenu, "Grid");
            }
            
            if (grid == null) {
                AE2CraftingLens.LOGGER.warn("Could not find grid in menu");
                return;
            }
            AE2CraftingLens.LOGGER.info("Found grid: {}", grid);
            
            AE2CraftingLens.LOGGER.info("Attempting to find pattern providers");
            Set<BlockPos> providerPositions = targetKey == null ? 
                findAllActivePatternProviders(grid, player.containerMenu) : 
                findPatternProvidersForKey(grid, targetKey);
            
            if (targetKey == null) {
                AE2CraftingLens.LOGGER.info("Found {} active pattern provider positions", providerPositions.size());
            } else {
                AE2CraftingLens.LOGGER.info("Found {} pattern provider positions for {}", providerPositions.size(), targetKey);
            }
            
            for (BlockPos pos : providerPositions) {
                AE2CraftingLens.LOGGER.info("Found provider at: {}", pos);
            }
            
            AE2CraftingLens.LOGGER.info("Creating response packet");
            PatternProviderResponsePacket response = new PatternProviderResponsePacket(providerPositions);
            AE2CraftingLens.LOGGER.info("Sending response packet to client");
            context.reply(response);
            AE2CraftingLens.LOGGER.info("Response packet sent successfully");
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error handling pattern provider request", e);
        }
        AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Server request processed ===");
    }
    
    private static Set<BlockPos> findPatternProvidersForKey(Object grid, Object targetKey) {
        Set<BlockPos> positions = new HashSet<>();
        
        try {
            Object craftingService = invokeMethod(grid, "getCraftingService", Object.class);
            if (craftingService == null) {
                AE2CraftingLens.LOGGER.warn("Could not get crafting service");
                return positions;
            }
            
            Class<?> patternDetailsClass = Class.forName("appeng.api.crafting.IPatternDetails");
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            
            Method getCraftingForMethod = craftingService.getClass().getMethod("getCraftingFor", aeKeyClass);
            Iterable<?> patterns = (Iterable<?>) getCraftingForMethod.invoke(craftingService, targetKey);
            
            if (patterns == null) {
                AE2CraftingLens.LOGGER.info("No patterns found for key {}", targetKey);
                return positions;
            }
            
            int patternCount = 0;
            for (Object pattern : patterns) {
                patternCount++;
                try {
                    Method getProvidersMethod = craftingService.getClass().getMethod("getProviders", patternDetailsClass);
                    Iterable<?> providers = (Iterable<?>) getProvidersMethod.invoke(craftingService, pattern);
                    
                    if (providers != null) {
                        for (Object provider : providers) {
                            BlockPos pos = getProviderPosition(provider);
                            if (pos != null) {
                                positions.add(pos);
                                AE2CraftingLens.LOGGER.debug("Found provider at {}", pos);
                            }
                        }
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.debug("Error getting providers for pattern: {}", e.getMessage());
                }
            }
            
            AE2CraftingLens.LOGGER.info("Checked {} patterns for key {}", patternCount, targetKey);
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error finding pattern provider positions", e);
        }
        
        return positions;
    }
    
    private static Set<BlockPos> findAllActivePatternProviders(Object grid, Object menu) {
        Set<BlockPos> positions = new HashSet<>();
        
        try {
            Object craftingService = invokeMethod(grid, "getCraftingService", Object.class);
            if (craftingService == null) {
                AE2CraftingLens.LOGGER.warn("Could not get crafting service");
                return positions;
            }
            
            // 方法1: 尝试从 CraftingStatusMenu 的 cpuList 获取活跃的合成作业
            if (menu != null && menu.getClass().getName().contains("CraftingStatusMenu")) {
                AE2CraftingLens.LOGGER.info("Attempting to get active providers from CraftingStatusMenu cpuList");
                positions.addAll(findProvidersFromCraftingStatusMenu(menu, grid, craftingService));
                if (!positions.isEmpty()) {
                    return positions;
                }
            }
            
            // 方法2: 尝试获取所有活跃的合成作业
            try {
                Method getJobsMethod = craftingService.getClass().getMethod("getJobs");
                Iterable<?> jobs = (Iterable<?>) getJobsMethod.invoke(craftingService);
                
                if (jobs != null) {
                    int jobCount = 0;
                    for (Object job : jobs) {
                        jobCount++;
                        try {
                            // 尝试从作业中获取相关的样板
                            Class<?> patternDetailsClass = Class.forName("appeng.api.crafting.IPatternDetails");
                            Method getPatternMethod = job.getClass().getMethod("getPattern");
                            Object pattern = getPatternMethod.invoke(job);
                            
                            if (pattern != null) {
                                Method getProvidersMethod = craftingService.getClass().getMethod("getProviders", patternDetailsClass);
                                Iterable<?> providers = (Iterable<?>) getProvidersMethod.invoke(craftingService, pattern);
                                
                                if (providers != null) {
                                    for (Object provider : providers) {
                                        BlockPos pos = getProviderPosition(provider);
                                        if (pos != null) {
                                            positions.add(pos);
                                            AE2CraftingLens.LOGGER.debug("Found active provider at {}", pos);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            AE2CraftingLens.LOGGER.debug("Error processing job: {}", e.getMessage());
                        }
                    }
                    AE2CraftingLens.LOGGER.info("Checked {} active jobs", jobCount);
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error getting jobs: {}", e.getMessage());
            }
            
            // 方法3: 尝试获取所有样板供应器
            if (positions.isEmpty()) {
                try {
                    Method getPatternProvidersMethod = grid.getClass().getMethod("getPatternProviders");
                    Iterable<?> providers = (Iterable<?>) getPatternProvidersMethod.invoke(grid);
                    
                    if (providers != null) {
                        int providerCount = 0;
                        for (Object provider : providers) {
                            providerCount++;
                            BlockPos pos = getProviderPosition(provider);
                            if (pos != null) {
                                positions.add(pos);
                                AE2CraftingLens.LOGGER.debug("Found pattern provider at {}", pos);
                            }
                        }
                        AE2CraftingLens.LOGGER.info("Found {} pattern providers via grid method", providerCount);
                    }
                } catch (Exception ex) {
                    AE2CraftingLens.LOGGER.debug("Error getting pattern providers: {}", ex.getMessage());
                }
            }
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error finding all active pattern providers", e);
        }
        
        return positions;
    }
    
    private static Set<BlockPos> findProvidersFromCraftingStatusMenu(Object menu, Object grid, Object craftingService) {
        Set<BlockPos> positions = new HashSet<>();
        
        try {
            // 获取 cpuList 字段
            Field cpuListField = menu.getClass().getDeclaredField("cpuList");
            cpuListField.setAccessible(true);
            Object cpuList = cpuListField.get(menu);
            
            if (cpuList == null) {
                AE2CraftingLens.LOGGER.info("cpuList is null");
                return positions;
            }
            
            AE2CraftingLens.LOGGER.info("Found cpuList: {}", cpuList);
            
            // 尝试获取 cpus 列表
            try {
                Field cpusField = cpuList.getClass().getDeclaredField("cpus");
                cpusField.setAccessible(true);
                Iterable<?> cpus = (Iterable<?>) cpusField.get(cpuList);
                
                if (cpus == null) {
                    AE2CraftingLens.LOGGER.info("cpus list is null");
                    return positions;
                }
                
                // 使用传入的 grid 和 craftingService
                AE2CraftingLens.LOGGER.info("Using provided grid: {} and craftingService: {}", grid, craftingService);
                
                int cpuCount = 0;
                for (Object cpu : cpus) {
                    cpuCount++;
                    try {
                        // 尝试获取当前作业 - 直接访问字段
                        Object currentJob = null;
                        try {
                            Field currentJobField = cpu.getClass().getDeclaredField("currentJob");
                            currentJobField.setAccessible(true);
                            currentJob = currentJobField.get(cpu);
                            AE2CraftingLens.LOGGER.info("Got currentJob field for CPU {}: {}", cpuCount, currentJob);
                        } catch (Exception e) {
                            AE2CraftingLens.LOGGER.debug("Error getting currentJob field: {}", e.getMessage());
                            // 备选：尝试方法
                            try {
                                Method getCurrentJobMethod = cpu.getClass().getMethod("getCurrentJob");
                                currentJob = getCurrentJobMethod.invoke(cpu);
                                AE2CraftingLens.LOGGER.info("Got currentJob via method for CPU {}: {}", cpuCount, currentJob);
                            } catch (Exception ex) {
                                AE2CraftingLens.LOGGER.debug("Error getting currentJob method: {}", ex.getMessage());
                            }
                        }
                        
                        if (currentJob != null) {
                            AE2CraftingLens.LOGGER.info("Found active job on CPU {}: {}", cpuCount, currentJob);
                            
                            // 使用已经获取的 craftingService
                            // currentJob 可能是 GenericStack 类型，需要获取 what 字段
                            Object aeKey = null;
                            try {
                                // 尝试从 GenericStack 获取 what 字段 (AEKey)
                                Method whatMethod = currentJob.getClass().getMethod("what");
                                aeKey = whatMethod.invoke(currentJob);
                                AE2CraftingLens.LOGGER.info("Found AEKey from currentJob: {}", aeKey);
                            } catch (Exception e) {
                                AE2CraftingLens.LOGGER.debug("Error getting what from currentJob: {}", e.getMessage());
                            }
                            
                            if (aeKey != null) {
                                AE2CraftingLens.LOGGER.info("Using AEKey to find patterns: {}", aeKey);
                                // 使用 AEKey 查找对应的样板
                                try {
                                    Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
                                    Class<?> patternDetailsClass = Class.forName("appeng.api.crafting.IPatternDetails");
                                    
                                    Method getCraftingForMethod = craftingService.getClass().getMethod("getCraftingFor", aeKeyClass);
                                    Iterable<?> patterns = (Iterable<?>) getCraftingForMethod.invoke(craftingService, aeKey);
                                    
                                    if (patterns != null) {
                                        int patternCount = 0;
                                        for (Object pattern : patterns) {
                                            patternCount++;
                                            AE2CraftingLens.LOGGER.info("Found pattern {}: {}", patternCount, pattern);
                                            try {
                                                Method getProvidersMethod = craftingService.getClass().getMethod("getProviders", patternDetailsClass);
                                                Iterable<?> providers = (Iterable<?>) getProvidersMethod.invoke(craftingService, pattern);
                                                
                                                if (providers != null) {
                                                    int providerCount = 0;
                                                    for (Object provider : providers) {
                                                        providerCount++;
                                                        AE2CraftingLens.LOGGER.info("Found provider {}: {}", providerCount, provider);
                                                        BlockPos pos = getProviderPosition(provider);
                                                        if (pos != null) {
                                                            positions.add(pos);
                                                            AE2CraftingLens.LOGGER.info("Found provider at {} for active job", pos);
                                                        } else {
                                                            AE2CraftingLens.LOGGER.warn("Could not get position for provider: {}", provider);
                                                        }
                                                    }
                                                    AE2CraftingLens.LOGGER.info("Total providers found for pattern: {}", providerCount);
                                                } else {
                                                    AE2CraftingLens.LOGGER.info("No providers found for pattern");
                                                }
                                            } catch (Exception e) {
                                                AE2CraftingLens.LOGGER.error("Error getting providers for pattern: {}", e.getMessage(), e);
                                            }
                                        }
                                        AE2CraftingLens.LOGGER.info("Total patterns found: {}", patternCount);
                                    } else {
                                        AE2CraftingLens.LOGGER.info("No patterns found for AEKey: {}", aeKey);
                                    }
                                } catch (Exception e) {
                                    AE2CraftingLens.LOGGER.error("Error finding patterns for AEKey: {}", e.getMessage(), e);
                                }
                                
                                // 备选方案：尝试从 craftingService 获取所有活跃的合成任务
                                try {
                                    AE2CraftingLens.LOGGER.info("Trying alternative method: getting active jobs from craftingService");
                                    Method getActiveJobsMethod = craftingService.getClass().getMethod("getActiveJobs");
                                    Iterable<?> activeJobs = (Iterable<?>) getActiveJobsMethod.invoke(craftingService);
                                    
                                    if (activeJobs != null) {
                                        int jobCount = 0;
                                        for (Object job : activeJobs) {
                                            jobCount++;
                                            AE2CraftingLens.LOGGER.info("Found active job {}: {}", jobCount, job);
                                            // 尝试从 job 获取样板供应器
                                            try {
                                                Method getPatternProviderMethod = job.getClass().getMethod("getPatternProvider");
                                                Object provider = getPatternProviderMethod.invoke(job);
                                                if (provider != null) {
                                                    AE2CraftingLens.LOGGER.info("Found pattern provider from job: {}", provider);
                                                    BlockPos pos = getProviderPosition(provider);
                                                    if (pos != null) {
                                                        positions.add(pos);
                                                        AE2CraftingLens.LOGGER.info("Found provider at {} from active job", pos);
                                                    }
                                                }
                                            } catch (Exception ex) {
                                                AE2CraftingLens.LOGGER.debug("Error getting pattern provider from job: {}", ex.getMessage());
                                            }
                                        }
                                        AE2CraftingLens.LOGGER.info("Total active jobs found: {}", jobCount);
                                    }
                                } catch (Exception ex) {
                                    AE2CraftingLens.LOGGER.debug("Error getting active jobs: {}", ex.getMessage());
                                }
                            } else {
                                AE2CraftingLens.LOGGER.warn("AEKey is null, cannot find patterns");
                            }
                        }
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.debug("Error processing CPU {}: {}", cpuCount, e.getMessage());
                    }
                }
                
                AE2CraftingLens.LOGGER.info("Processed {} CPUs from cpuList", cpuCount);
                
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.error("Error accessing cpus list: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error finding providers from CraftingStatusMenu: {}", e.getMessage());
        }
        
        return positions;
    }
    
    private static Object findGridFromCraftingStatusMenu(Object menu) {
        try {
            // 方法1: 直接获取 grid 字段
            try {
                Field gridField = menu.getClass().getDeclaredField("grid");
                gridField.setAccessible(true);
                Object grid = gridField.get(menu);
                if (grid != null) {
                    AE2CraftingLens.LOGGER.info("Found grid field directly in CraftingStatusMenu: {}", grid);
                    return grid;
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Could not get grid field directly: {}", e.getMessage());
            }
            
            // 方法2: 尝试找到 cpu 字段
            Object cpu = findFieldByTypeName(menu, "CraftingCPU");
            if (cpu != null) {
                AE2CraftingLens.LOGGER.info("Found CPU in CraftingStatusMenu: {}", cpu);
                // 尝试从 cpu 获取 grid
                Object grid = invokeMethod(cpu, "getGrid", Object.class);
                if (grid != null) {
                    return grid;
                }
            }
            
            // 方法3: 尝试找到 cluster 字段
            Object cluster = findFieldByTypeName(menu, "Cluster");
            if (cluster != null) {
                AE2CraftingLens.LOGGER.info("Found cluster in CraftingStatusMenu: {}", cluster);
                Object grid = invokeMethod(cluster, "getGrid", Object.class);
                if (grid != null) {
                    return grid;
                }
            }
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error finding grid from CraftingStatusMenu", e);
        }
        return null;
    }
    
    private static Object findFieldByTypeName(Object obj, String typeName) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    if (value != null) {
                        // 检查字段类型名称是否包含目标类型名
                        if (value.getClass().getName().contains(typeName)) {
                            AE2CraftingLens.LOGGER.info("Found field with type containing {}: {}", typeName, field.getName());
                            return value;
                        }
                        // 检查字段名称是否包含目标类型名
                        if (field.getName().contains(typeName)) {
                            AE2CraftingLens.LOGGER.info("Found field with name containing {}: {}", typeName, field.getName());
                            return value;
                        }
                    }
                } catch (IllegalAccessException e) {
                    // ignore
                }
            }
            clazz = clazz.getSuperclass();
        }
        AE2CraftingLens.LOGGER.info("Could not find field containing {}", typeName);
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private static <T> T invokeMethod(Object obj, String methodName, Class<T> returnType) {
        for (Method method : obj.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                try {
                    Object result = method.invoke(obj);
                    if (returnType.isInstance(result)) {
                        return (T) result;
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.debug("Error invoking method {}: {}", methodName, e.getMessage());
                }
            }
        }
        return null;
    }
    
    private static BlockPos getProviderPosition(Object provider) {
        try {
            String providerClassName = provider.getClass().getName();
            AE2CraftingLens.LOGGER.debug("Provider class: {}", providerClassName);
            
            // 检查是否是 ExtendedAE 的 ex_pattern_provider
            boolean isExtendedAEProvider = providerClassName.contains("ExPattern") || 
                                         providerClassName.contains("ex_pattern") ||
                                         providerClassName.contains("ExtendedAE") ||
                                         providerClassName.contains("PartExPattern");
            
            AE2CraftingLens.LOGGER.debug("Is ExtendedAE provider: {}", isExtendedAEProvider);
            
            // 方法0: 检查 provider 本身是否实现了 PatternProviderLogicHost 接口
            Object host = null;
            boolean isPatternProviderLogicHost = false;
            try {
                // 尝试查找 PatternProviderLogicHost 接口
                Class<?> patternProviderLogicHostClass = null;
                try {
                    patternProviderLogicHostClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogicHost");
                } catch (ClassNotFoundException e) {
                    try {
                        patternProviderLogicHostClass = Class.forName("appeng.api.helpers.IPatternProviderLogicHost");
                    } catch (ClassNotFoundException e2) {
                        // 接口未找到，继续其他方法
                    }
                }
                
                if (patternProviderLogicHostClass != null && patternProviderLogicHostClass.isInstance(provider)) {
                    AE2CraftingLens.LOGGER.debug("Provider implements PatternProviderLogicHost interface");
                    isPatternProviderLogicHost = true;
                    host = provider; // provider 本身就是 host
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error checking PatternProviderLogicHost interface: {}", e.getMessage());
            }
            
            // 方法1: 如果 provider 本身不是 PatternProviderLogicHost，尝试找到 PatternProviderLogicHost 或 PatternProviderBlockEntity 字段
            if (host == null) {
                host = findFieldByTypeName(provider, "PatternProviderLogicHost");
                if (host == null) {
                    host = findFieldByTypeName(provider, "PatternProviderBlockEntity");
                }
            }
            
            // 如果是 ExtendedAE 提供器，尝试查找 ExtendedAE 特定的字段
            if (isExtendedAEProvider && host == null) {
                AE2CraftingLens.LOGGER.debug("Searching for ExtendedAE-specific fields");
                host = findFieldByTypeName(provider, "ExPattern");
                if (host == null) {
                    host = findFieldByTypeName(provider, "ex_pattern");
                }
                if (host == null) {
                    host = findFieldByTypeName(provider, "PartEx");
                }
                if (host == null) {
                    host = findFieldByTypeName(provider, "ExtendedAE");
                }
                if (host == null) {
                    // 尝试查找包含 "Provider" 但不包含 "Pattern" 的字段（ExtendedAE 可能使用不同的命名）
                    try {
                        Class<?> clazz = provider.getClass();
                        while (clazz != null) {
                            for (Field field : clazz.getDeclaredFields()) {
                                field.setAccessible(true);
                                try {
                                    Object value = field.get(provider);
                                    if (value != null) {
                                        String className = value.getClass().getName();
                                        if (className.contains("Provider") && !className.contains("Pattern")) {
                                            AE2CraftingLens.LOGGER.debug("Found ExtendedAE provider-like field: {} with type {}", field.getName(), className);
                                            host = value;
                                            break;
                                        }
                                    }
                                } catch (IllegalAccessException e) {
                                    // ignore
                                }
                            }
                            if (host != null) break;
                            clazz = clazz.getSuperclass();
                        }
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.debug("Error searching for ExtendedAE provider fields: {}", e.getMessage());
                    }
                }
            }
            
            // 方法2: 尝试找到 host 字段（适用于 PartExPatternProvider）
            if (host == null) {
                try {
                    Method getHostMethod = provider.getClass().getMethod("getHost");
                    host = getHostMethod.invoke(provider);
                    if (host != null) {
                        AE2CraftingLens.LOGGER.debug("Found host via getHost() method");
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.debug("Error calling getHost() method: {}", e.getMessage());
                }
            }
            
            if (host != null) {
                // 尝试从 host 获取 blockEntity
                Object blockEntity = invokeMethod(host, "getBlockEntity", Object.class);
                if (blockEntity == null) {
                    // 尝试从 host 获取位置直接
                    BlockPos pos = invokeMethod(host, "getBlockPos", BlockPos.class);
                    if (pos != null) {
                        AE2CraftingLens.LOGGER.debug("Found position directly from host: {}", pos);
                        return pos;
                    }
                    blockEntity = host;
                }
                
                BlockPos pos = invokeMethod(blockEntity, "getBlockPos", BlockPos.class);
                if (pos != null) {
                    AE2CraftingLens.LOGGER.debug("Found position from blockEntity: {}", pos);
                    return pos;
                }
            }
            
            // 方法3: 直接在 provider 上查找 getBlockPos 方法
            for (Method method : provider.getClass().getMethods()) {
                if (method.getName().equals("getBlockPos") && method.getParameterCount() == 0) {
                    try {
                        Object result = method.invoke(provider);
                        if (result instanceof BlockPos) {
                            AE2CraftingLens.LOGGER.debug("Found position via getBlockPos() method: {}", result);
                            return (BlockPos) result;
                        }
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.debug("Error calling getBlockPos() method: {}", e.getMessage());
                    }
                }
            }
            
            // 方法4: 尝试从 provider 的父类或其他字段获取位置
            try {
                Object part = findFieldByTypeName(provider, "Part");
                if (part != null) {
                    BlockPos pos = invokeMethod(part, "getBlockPos", BlockPos.class);
                    if (pos != null) {
                        AE2CraftingLens.LOGGER.debug("Found position from part: {}", pos);
                        return pos;
                    }
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error getting position from part: {}", e.getMessage());
            }
            
            // 方法5: 针对 ExtendedAE 的特殊处理
            if (isExtendedAEProvider) {
                AE2CraftingLens.LOGGER.debug("Applying ExtendedAE-specific detection methods");
                
                // 尝试查找 ExtendedAE 特定的字段
                try {
                    // ExtendedAE 可能使用不同的字段名
                    Object extendedHost = findFieldByTypeName(provider, "ExPatternProviderHost");
                    if (extendedHost == null) {
                        extendedHost = findFieldByTypeName(provider, "PartExPatternProvider");
                    }
                    if (extendedHost == null) {
                        extendedHost = findFieldByTypeName(provider, "ExPatternPart");
                    }
                    
                    if (extendedHost != null) {
                        AE2CraftingLens.LOGGER.debug("Found ExtendedAE host: {}", extendedHost);
                        
                        // 尝试从 ExtendedAE host 获取位置
                        BlockPos pos = invokeMethod(extendedHost, "getBlockPos", BlockPos.class);
                        if (pos != null) {
                            AE2CraftingLens.LOGGER.debug("Found position from ExtendedAE host: {}", pos);
                            return pos;
                        }
                        
                        // 尝试获取 blockEntity
                        Object blockEntity = invokeMethod(extendedHost, "getBlockEntity", Object.class);
                        if (blockEntity != null) {
                            pos = invokeMethod(blockEntity, "getBlockPos", BlockPos.class);
                            if (pos != null) {
                                AE2CraftingLens.LOGGER.debug("Found position from ExtendedAE blockEntity: {}", pos);
                                return pos;
                            }
                        }
                    }
                    
                    // 尝试直接调用 ExtendedAE 特定的方法
                    for (Method method : provider.getClass().getMethods()) {
                        String methodName = method.getName();
                        if ((methodName.equals("getLocation") || methodName.equals("getPos") || 
                             methodName.equals("getPosition") || methodName.equals("location")) && 
                            method.getParameterCount() == 0) {
                            try {
                                Object result = method.invoke(provider);
                                if (result instanceof BlockPos) {
                                    AE2CraftingLens.LOGGER.debug("Found position via ExtendedAE method {}: {}", methodName, result);
                                    return (BlockPos) result;
                                }
                            } catch (Exception e) {
                                AE2CraftingLens.LOGGER.debug("Error calling ExtendedAE method {}: {}", methodName, e.getMessage());
                            }
                        }
                    }
                    
                    // 尝试查找 part 或 tile 字段
                    Object part = findFieldByTypeName(provider, "part");
                    if (part == null) {
                        part = findFieldByTypeName(provider, "tile");
                    }
                    if (part == null) {
                        part = findFieldByTypeName(provider, "blockEntity");
                    }
                    
                    if (part != null) {
                        BlockPos pos = invokeMethod(part, "getBlockPos", BlockPos.class);
                        if (pos != null) {
                            AE2CraftingLens.LOGGER.debug("Found position from ExtendedAE part/tile: {}", pos);
                            return pos;
                        }
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.debug("Error in ExtendedAE-specific detection: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error getting provider position: {}", e.getMessage());
        }
        return null;
    }
}
