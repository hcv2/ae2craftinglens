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
            AE2CraftingLens.LOGGER.info("Received null AEKey, looking for all active pattern providers in current crafting job");
        } else {
            AE2CraftingLens.LOGGER.info("Looking for pattern providers for: {}", targetKey);
        }
        
        if (player.containerMenu == null) {
            AE2CraftingLens.LOGGER.info("No container menu open");
            return;
        }
        
        String containerClassName = player.containerMenu.getClass().getName();
        AE2CraftingLens.LOGGER.info("Container class: {}", containerClassName);
        
        if (!containerClassName.contains("Crafting") && !containerClassName.contains("crafting")) {
            AE2CraftingLens.LOGGER.info("Not a crafting menu");
            return;
        }
        
        AE2CraftingLens.LOGGER.info("Crafting menu detected");
        
        try {
            Object grid = null;
            
            if (containerClassName.contains("CraftingStatusMenu")) {
                AE2CraftingLens.LOGGER.info("Attempting to get grid from CraftingStatusMenu");
                grid = findGridFromCraftingStatusMenu(player.containerMenu);
            }
            
            if (grid == null) {
                AE2CraftingLens.LOGGER.info("Attempting to find CraftingCPUCluster");
                Object cluster = findFieldByTypeName(player.containerMenu, "CraftingCPUCluster");
                if (cluster != null) {
                    AE2CraftingLens.LOGGER.info("Found CraftingCPUCluster: {}", cluster);
                    grid = invokeMethod(cluster, "getGrid", Object.class);
                }
            }
            
            if (grid == null) {
                AE2CraftingLens.LOGGER.info("Attempting to get grid directly from menu");
                grid = invokeMethod(player.containerMenu, "getGrid", Object.class);
            }
            
            if (grid == null) {
                AE2CraftingLens.LOGGER.info("Attempting to find grid field");
                grid = findFieldByTypeName(player.containerMenu, "Grid");
            }
            
            if (grid == null) {
                AE2CraftingLens.LOGGER.warn("Could not find grid in menu");
                return;
            }
            AE2CraftingLens.LOGGER.info("Found grid: {}", grid);
            
            Object craftingService = invokeMethod(grid, "getCraftingService", Object.class);
            if (craftingService == null) {
                AE2CraftingLens.LOGGER.warn("Could not get crafting service");
                return;
            }
            
            Set<BlockPos> providerPositions = new HashSet<>();
            
            if (containerClassName.contains("CraftingStatusMenu")) {
                providerPositions = findProvidersFromCurrentCraftingJob(player.containerMenu, craftingService, targetKey);
            }
            
            if (providerPositions.isEmpty()) {
                providerPositions = targetKey == null ? 
                    findAllActivePatternProviders(grid, player.containerMenu) : 
                    findPatternProvidersForKey(grid, targetKey);
            }
            
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
    
    private static Set<BlockPos> findProvidersFromCurrentCraftingJob(Object menu, Object craftingService, Object targetKey) {
        Set<BlockPos> positions = new HashSet<>();
        
        try {
            AE2CraftingLens.LOGGER.info("Finding providers from current crafting job in CraftingStatusMenu");
            
            Field cpuListField = menu.getClass().getDeclaredField("cpuList");
            cpuListField.setAccessible(true);
            Object cpuList = cpuListField.get(menu);
            
            if (cpuList == null) {
                AE2CraftingLens.LOGGER.info("cpuList is null");
                return positions;
            }
            
            Field cpusField = cpuList.getClass().getDeclaredField("cpus");
            cpusField.setAccessible(true);
            Iterable<?> cpus = (Iterable<?>) cpusField.get(cpuList);
            
            if (cpus == null) {
                AE2CraftingLens.LOGGER.info("cpus list is null");
                return positions;
            }
            
            int cpuCount = 0;
            for (Object cpu : cpus) {
                cpuCount++;
                try {
                    Object cluster = null;
                    try {
                        Field clusterField = cpu.getClass().getDeclaredField("cluster");
                        clusterField.setAccessible(true);
                        cluster = clusterField.get(cpu);
                        AE2CraftingLens.LOGGER.info("Got cluster field for CPU {}: {}", cpuCount, cluster);
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.debug("Error getting cluster field: {}", e.getMessage());
                    }
                    
                    if (cluster == null) {
                        cluster = cpu;
                    }
                    
                    Set<BlockPos> clusterProviders = findProvidersFromClusterForTarget(cluster, craftingService, targetKey);
                    positions.addAll(clusterProviders);
                    
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.debug("Error processing CPU {}: {}", cpuCount, e.getMessage());
                }
            }
            
            AE2CraftingLens.LOGGER.info("Processed {} CPUs from cpuList", cpuCount);
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error finding providers from current crafting job: {}", e.getMessage());
        }
        
        return positions;
    }
    
    private static Set<BlockPos> findProvidersFromClusterForTarget(Object cluster, Object craftingService, Object targetKey) {
        Set<BlockPos> positions = new HashSet<>();
        
        try {
            AE2CraftingLens.LOGGER.info("Finding providers from cluster for target: {}", targetKey);
            
            Set<Object> relevantPatterns = new HashSet<>();
            
            Object craftingLogic = null;
            try {
                Field craftingLogicField = cluster.getClass().getField("craftingLogic");
                craftingLogic = craftingLogicField.get(cluster);
                AE2CraftingLens.LOGGER.info("Found craftingLogic field: {}", craftingLogic);
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error getting craftingLogic field: {}", e.getMessage());
                try {
                    craftingLogic = findFieldByTypeName(cluster, "CraftingCpuLogic");
                } catch (Exception e2) {
                    AE2CraftingLens.LOGGER.debug("Error finding CraftingCpuLogic: {}", e2.getMessage());
                }
            }
            
            if (craftingLogic != null) {
                Object job = null;
                try {
                    Field jobField = craftingLogic.getClass().getDeclaredField("job");
                    jobField.setAccessible(true);
                    job = jobField.get(craftingLogic);
                    AE2CraftingLens.LOGGER.info("Found job from craftingLogic: {}", job != null);
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.debug("Error getting job field: {}", e.getMessage());
                }
                
                if (job != null) {
                    try {
                        Field tasksField = job.getClass().getDeclaredField("tasks");
                        tasksField.setAccessible(true);
                        Object tasks = tasksField.get(job);
                        
                        if (tasks instanceof java.util.Map) {
                            java.util.Map<?, ?> tasksMap = (java.util.Map<?, ?>) tasks;
                            AE2CraftingLens.LOGGER.info("Found {} tasks in job", tasksMap.size());
                            
                            for (Object pattern : tasksMap.keySet()) {
                                if (pattern != null) {
                                    relevantPatterns.add(pattern);
                                    AE2CraftingLens.LOGGER.info("Found pattern from job.tasks: {}", pattern);
                                }
                            }
                        }
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.debug("Error getting tasks from job: {}", e.getMessage());
                    }
                    
                    try {
                        Field waitingForField = job.getClass().getDeclaredField("waitingFor");
                        waitingForField.setAccessible(true);
                        Object waitingFor = waitingForField.get(job);
                        
                        if (waitingFor != null) {
                            AE2CraftingLens.LOGGER.info("Found waitingFor field in job");
                        }
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.debug("Error getting waitingFor from job: {}", e.getMessage());
                    }
                }
            }
            
            try {
                Method getWaitingTasksMethod = cluster.getClass().getMethod("getWaitingTasks");
                Iterable<?> waitingTasks = (Iterable<?>) getWaitingTasksMethod.invoke(cluster);
                
                if (waitingTasks != null) {
                    AE2CraftingLens.LOGGER.info("Found waiting tasks");
                    for (Object task : waitingTasks) {
                        try {
                            Method getPatternMethod = task.getClass().getMethod("getPattern");
                            Object pattern = getPatternMethod.invoke(task);
                            if (pattern != null) {
                                relevantPatterns.add(pattern);
                                AE2CraftingLens.LOGGER.info("Found pattern from waiting task: {}", pattern);
                            }
                        } catch (Exception e) {
                            AE2CraftingLens.LOGGER.debug("Error getting pattern from waiting task: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error getting waiting tasks: {}", e.getMessage());
            }
            
            Class<?> patternDetailsClass = Class.forName("appeng.api.crafting.IPatternDetails");
            Method getProvidersMethod = craftingService.getClass().getMethod("getProviders", patternDetailsClass);
            
            for (Object pattern : relevantPatterns) {
                try {
                    Iterable<?> providers = (Iterable<?>) getProvidersMethod.invoke(craftingService, pattern);
                    
                    if (providers != null) {
                        for (Object provider : providers) {
                            BlockPos pos = getProviderPosition(provider);
                            if (pos != null) {
                                positions.add(pos);
                                AE2CraftingLens.LOGGER.info("Found provider at {} for pattern", pos);
                            }
                        }
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.debug("Error getting providers for pattern: {}", e.getMessage());
                }
            }
            
            AE2CraftingLens.LOGGER.info("Total relevant patterns found: {}, total providers: {}", relevantPatterns.size(), positions.size());
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error finding providers from cluster for target: {}", e.getMessage());
        }
        
        return positions;
    }
    
    private static boolean isPatternRelevantToTarget(Object pattern, Object targetKey, Object craftingService) {
        try {
            Method getPrimaryOutputMethod = pattern.getClass().getMethod("getPrimaryOutput");
            Object primaryOutput = getPrimaryOutputMethod.invoke(pattern);
            
            if (primaryOutput != null) {
                Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
                if (genericStackClass.isInstance(primaryOutput)) {
                    Method whatMethod = genericStackClass.getMethod("what");
                    Object outputKey = whatMethod.invoke(primaryOutput);
                    
                    if (outputKey != null && outputKey.equals(targetKey)) {
                        AE2CraftingLens.LOGGER.debug("Pattern primary output matches target: {}", outputKey);
                        return true;
                    }
                }
            }
            
            try {
                Method getOutputsMethod = pattern.getClass().getMethod("getOutputs");
                Object outputs = getOutputsMethod.invoke(pattern);
                
                if (outputs != null && outputs instanceof Object[]) {
                    for (Object output : (Object[]) outputs) {
                        Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
                        if (genericStackClass.isInstance(output)) {
                            Method whatMethod = genericStackClass.getMethod("what");
                            Object outputKey = whatMethod.invoke(output);
                            
                            if (outputKey != null && outputKey.equals(targetKey)) {
                                AE2CraftingLens.LOGGER.debug("Pattern output matches target: {}", outputKey);
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error checking outputs: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error checking pattern relevance: {}", e.getMessage());
        }
        
        return false;
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
            
            try {
                Method getJobsMethod = craftingService.getClass().getMethod("getJobs");
                Iterable<?> jobs = (Iterable<?>) getJobsMethod.invoke(craftingService);
                
                if (jobs != null) {
                    int jobCount = 0;
                    for (Object job : jobs) {
                        jobCount++;
                        try {
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
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error finding all active pattern providers", e);
        }
        
        return positions;
    }
    
    private static Object findGridFromCraftingStatusMenu(Object menu) {
        try {
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
            
            Object cpu = findFieldByTypeName(menu, "CraftingCPU");
            if (cpu != null) {
                AE2CraftingLens.LOGGER.info("Found CPU in CraftingStatusMenu: {}", cpu);
                Object grid = invokeMethod(cpu, "getGrid", Object.class);
                if (grid != null) {
                    return grid;
                }
            }
            
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
                        if (value.getClass().getName().contains(typeName)) {
                            AE2CraftingLens.LOGGER.info("Found field with type containing {}: {}", typeName, field.getName());
                            return value;
                        }
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
            
            boolean isExtendedAEProvider = providerClassName.contains("ExPattern") || 
                                         providerClassName.contains("ex_pattern") ||
                                         providerClassName.contains("ExtendedAE") ||
                                         providerClassName.contains("PartExPattern");
            
            AE2CraftingLens.LOGGER.debug("Is ExtendedAE provider: {}", isExtendedAEProvider);
            
            Object host = null;
            try {
                Class<?> patternProviderLogicHostClass = null;
                try {
                    patternProviderLogicHostClass = Class.forName("appeng.helpers.patternprovider.PatternProviderLogicHost");
                } catch (ClassNotFoundException e) {
                    try {
                        patternProviderLogicHostClass = Class.forName("appeng.api.helpers.IPatternProviderLogicHost");
                    } catch (ClassNotFoundException e2) {
                        // continue
                    }
                }
                
                if (patternProviderLogicHostClass != null && patternProviderLogicHostClass.isInstance(provider)) {
                    AE2CraftingLens.LOGGER.debug("Provider implements PatternProviderLogicHost interface");
                    host = provider;
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Error checking PatternProviderLogicHost interface: {}", e.getMessage());
            }
            
            if (host == null) {
                host = findFieldByTypeName(provider, "PatternProviderLogicHost");
                if (host == null) {
                    host = findFieldByTypeName(provider, "PatternProviderBlockEntity");
                }
            }
            
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
            }
            
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
                Object blockEntity = invokeMethod(host, "getBlockEntity", Object.class);
                if (blockEntity == null) {
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
            
            if (isExtendedAEProvider) {
                AE2CraftingLens.LOGGER.debug("Applying ExtendedAE-specific detection methods");
                
                try {
                    Object extendedHost = findFieldByTypeName(provider, "ExPatternProviderHost");
                    if (extendedHost == null) {
                        extendedHost = findFieldByTypeName(provider, "PartExPatternProvider");
                    }
                    if (extendedHost == null) {
                        extendedHost = findFieldByTypeName(provider, "ExPatternPart");
                    }
                    
                    if (extendedHost != null) {
                        AE2CraftingLens.LOGGER.debug("Found ExtendedAE host: {}", extendedHost);
                        
                        BlockPos pos = invokeMethod(extendedHost, "getBlockPos", BlockPos.class);
                        if (pos != null) {
                            AE2CraftingLens.LOGGER.debug("Found position from ExtendedAE host: {}", pos);
                            return pos;
                        }
                        
                        Object blockEntity = invokeMethod(extendedHost, "getBlockEntity", Object.class);
                        if (blockEntity != null) {
                            pos = invokeMethod(blockEntity, "getBlockPos", BlockPos.class);
                            if (pos != null) {
                                AE2CraftingLens.LOGGER.debug("Found position from ExtendedAE blockEntity: {}", pos);
                                return pos;
                            }
                        }
                    }
                    
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
