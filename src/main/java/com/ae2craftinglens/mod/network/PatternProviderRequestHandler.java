package com.ae2craftinglens.mod.network;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

public class PatternProviderRequestHandler {

    private record ProviderLocation(ResourceKey<Level> dimension, BlockPos pos) {}
    
    private interface ProviderStrategy {
        BlockPos extractPosition(Object provider);
        
        boolean canHandle(Object provider);
    }
    
    private static final List<ProviderStrategy> PROVIDER_STRATEGIES = List.of(
        new AdvancedAEProviderStrategy(),
        new ExtendedAEProviderStrategy(),
        new StandardAE2ProviderStrategy(),
        new FallbackProviderStrategy()
    );


    public static void handle(RequestPatternProvidersPacket packet, NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }

        Object targetKey = packet.getWhat();
        int rowIndex = packet.getRowIndex();
        
        if (player.containerMenu == null) {
            return;
        }
        
        String containerClassName = player.containerMenu.getClass().getName();
        
        if (!containerClassName.contains("Crafting") && !containerClassName.contains("crafting")) {
            return;
        }
        
        try {
            Object grid = null;
            
            if (containerClassName.contains("CraftingStatusMenu")) {
                grid = findGridFromCraftingStatusMenu(player.containerMenu);
            }
            
            if (grid == null) {
                Object cluster = findFieldByTypeName(player.containerMenu, "CraftingCPUCluster");
                if (cluster != null) {
                    grid = invokeMethod(cluster, "getGrid", Object.class);
                }
            }
            
            if (grid == null) {
                grid = invokeMethod(player.containerMenu, "getGrid", Object.class);
            }
            
            if (grid == null) {
                grid = findFieldByTypeName(player.containerMenu, "Grid");
            }
            
            if (grid == null) {
                AE2CraftingLens.LOGGER.warn("Could not find grid in menu");
                return;
            }
            
            Object craftingService = invokeMethod(grid, "getCraftingService", Object.class);
            if (craftingService == null) {
                AE2CraftingLens.LOGGER.warn("Could not get crafting service");
                return;
            }
            
            Set<ProviderLocation> providerLocations = new HashSet<>();

            if (containerClassName.contains("CraftingStatusMenu")) {
                providerLocations = findProvidersFromCurrentCraftingJob(grid, player.containerMenu, craftingService, targetKey, rowIndex, player.level());
            }

            if (providerLocations.isEmpty()) {
                providerLocations = targetKey == null ?
                    findAllActivePatternProviders(grid, player.containerMenu, player.level()) :
                    findPatternProvidersForKey(grid, targetKey, player.level());
            }
            
            Map<ResourceKey<Level>, Set<BlockPos>> positionsMap = new HashMap<>();
            for (ProviderLocation loc : providerLocations) {
                positionsMap.computeIfAbsent(loc.dimension(), k -> new HashSet<>()).add(loc.pos());
            }
            
            PatternProviderResponsePacket response = new PatternProviderResponsePacket(positionsMap);
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), response);
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error handling pattern provider request", e);
        }
    }
    
    private static Set<ProviderLocation> findProvidersFromCurrentCraftingJob(Object grid, Object menu, Object craftingService, Object targetKey, int rowIndex, Level defaultLevel) {
        Set<ProviderLocation> positions = new HashSet<>();

        try {
            int selectedCpuSerial = -1;
            try {
                Method getSelectedCpuSerialMethod = menu.getClass().getMethod("getSelectedCpuSerial");
                selectedCpuSerial = (int) getSelectedCpuSerialMethod.invoke(menu);
            } catch (Exception e) {
                // ignore
            }

            Set<Object> relevantPatterns = new HashSet<>();

            try {
                Method getJobsMethod = craftingService.getClass().getMethod("getJobs");
                Object jobsResult = getJobsMethod.invoke(craftingService);

                if (jobsResult != null) {
                    Iterable<?> jobs = null;
                    if (jobsResult instanceof Iterable) {
                        jobs = (Iterable<?>) jobsResult;
                    }
                    
                    if (jobs != null) {
                        for (Object job : jobs) {
                            try {
                                Object pattern = null;
                                
                                for (Method m : job.getClass().getMethods()) {
                                    if (m.getName().equals("getPattern") && m.getParameterCount() == 0) {
                                        pattern = m.invoke(job);
                                        break;
                                    }
                                }
                                
                                if (pattern != null) {
                                    if (targetKey == null || isPatternOutputsItem(pattern, targetKey)) {
                                        relevantPatterns.add(pattern);
                                    }
                                }
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }
            
            if (!relevantPatterns.isEmpty()) {
                Class<?> patternDetailsClass = Class.forName("appeng.api.crafting.IPatternDetails");
                Method getProvidersMethod = craftingService.getClass().getMethod("getProviders", patternDetailsClass);
                
                for (Object pattern : relevantPatterns) {
                    try {
                        Iterable<?> providers = (Iterable<?>) getProvidersMethod.invoke(craftingService, pattern);
                        
                        if (providers != null) {
                            for (Object provider : providers) {
                                BlockPos pos = getProviderPosition(provider);
                                if (pos != null) {
                                    Level level = getProviderLevel(provider, defaultLevel);
                                    positions.add(new ProviderLocation(level.dimension(), pos));
                                }
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            } else {
                Object cluster = findClusterFromGrid(grid, selectedCpuSerial);
                
                if (cluster != null) {
                    Set<ProviderLocation> clusterProviders = findProvidersFromClusterForTarget(cluster, craftingService, targetKey, true, defaultLevel);
                    
                    if (!clusterProviders.isEmpty()) {
                        return clusterProviders;
                    }
                }
                
                Object cluster2 = deepFindCraftingCPUCluster(menu);
                
                if (cluster2 != null) {
                    Set<ProviderLocation> clusterProviders = findProvidersFromClusterForTarget(cluster2, craftingService, targetKey, true, defaultLevel);
                    
                    if (!clusterProviders.isEmpty()) {
                        return clusterProviders;
                    }
                }
            }
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error finding providers from current crafting job: {}", e.getMessage());
        }
        
        return positions;
    }
    
    private static Object findClusterFromGrid(Object grid, int cpuSerial) {
        if (grid == null) return null;
        
        try {
            Method getCpusMethod = null;
            for (Method m : grid.getClass().getMethods()) {
                if (m.getName().equals("getCpus") && m.getParameterCount() == 0) {
                    getCpusMethod = m;
                    break;
                }
            }
            
            if (getCpusMethod == null) {
                return null;
            }
            
            Object cpusResult = getCpusMethod.invoke(grid);
            
            if (cpusResult == null) {
                return null;
            }
            
            Iterable<?> cpus = null;
            if (cpusResult instanceof Iterable) {
                cpus = (Iterable<?>) cpusResult;
            } else if (cpusResult instanceof Collection) {
                cpus = (Iterable<?>) cpusResult;
            }
            
            if (cpus == null) {
                return null;
            }
            
            for (Object cpu : cpus) {
                try {
                    int serial = -1;
                    
                    for (Method m : cpu.getClass().getMethods()) {
                        if (m.getName().equals("getSerial") && m.getParameterCount() == 0) {
                            serial = (int) m.invoke(cpu);
                            break;
                        }
                    }
                    
                    if (serial == -1) {
                        for (Field f : cpu.getClass().getDeclaredFields()) {
                            if (f.getName().contains("Serial") || f.getName().equals("serial")) {
                                f.setAccessible(true);
                                Object val = f.get(cpu);
                                if (val instanceof Integer) {
                                    serial = (int) val;
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (serial == cpuSerial) {
                        if (cpu.getClass().getName().contains("CraftingCPUCluster")) {
                            return cpu;
                        }
                        
                        for (Field f : cpu.getClass().getDeclaredFields()) {
                            f.setAccessible(true);
                            Object val = f.get(cpu);
                            if (val != null && val.getClass().getName().contains("CraftingCPUCluster")) {
                                return val;
                            }
                        }
                        
                        return cpu;
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error getting CPUs from grid", e);
        }
        
        return null;
    }
    
    private static Object deepFindCraftingCPUCluster(Object obj) {
        if (obj == null) return null;
        
        Set<Object> visited = new HashSet<>();
        return deepFindCraftingCPUClusterRecursive(obj, visited, 0);
    }
    
    private static Object deepFindCraftingCPUClusterRecursive(Object obj, Set<Object> visited, int depth) {
        if (obj == null || depth > 5 || visited.contains(obj)) return null;
        visited.add(obj);
        
        String className = obj.getClass().getName();
        if (className.contains("CraftingCPUCluster")) {
            return obj;
        }
        
        for (Field field : obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object fieldValue = field.get(obj);
                if (fieldValue != null) {
                    String fieldClassName = fieldValue.getClass().getName();
                    
                    if (fieldClassName.contains("CraftingCPUCluster")) {
                        return fieldValue;
                    }
                    
                    if (!fieldValue.getClass().getName().startsWith("java.") && 
                        !fieldValue.getClass().getName().startsWith("net.minecraft")) {
                        Object result = deepFindCraftingCPUClusterRecursive(fieldValue, visited, depth + 1);
                        if (result != null) return result;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        for (Method method : obj.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && !method.getReturnType().equals(Void.TYPE)) {
                String methodName = method.getName();
                if (methodName.contains("Cpu") || methodName.contains("Cluster") || 
                    methodName.contains("cpu") || methodName.contains("cluster")) {
                    try {
                        Object result = method.invoke(obj);
                        if (result != null && result.getClass().getName().contains("CraftingCPUCluster")) {
                            return result;
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
        
        return null;
    }
    
    private static Set<ProviderLocation> findProvidersFromClusterForTarget(Object cluster, Object craftingService, Object targetKey, boolean isSelectedCpu, Level defaultLevel) {
        Set<ProviderLocation> positions = new HashSet<>();
        
        try {
            Set<Object> relevantPatterns = new HashSet<>();
            
            Object craftingLogic = null;
            
            String[] logicFieldNames = {"logic", "craftingLogic", "f_legacy_logic_"};
            for (String fieldName : logicFieldNames) {
                try {
                    Field craftingLogicField = cluster.getClass().getDeclaredField(fieldName);
                    craftingLogicField.setAccessible(true);
                    craftingLogic = craftingLogicField.get(cluster);
                    break;
                } catch (Exception e) {
                    // Try next field name
                }
            }
            
            if (craftingLogic == null) {
                // AE2 1.20.1: Use CraftingLogic instead of CraftingCpuLogic
                craftingLogic = findFieldByTypeName(cluster, "CraftingLogic");
                if (craftingLogic == null) {
                    craftingLogic = findFieldByTypeName(cluster, "CraftingLogic");
                }
            }
            
            if (craftingLogic != null) {
                Object job = null;
                try {
                    Field jobField = craftingLogic.getClass().getDeclaredField("job");
                    jobField.setAccessible(true);
                    job = jobField.get(craftingLogic);
                } catch (Exception e) {
                    // ignore
                }
                
                if (job != null) {
                    try {
                        Field tasksField = job.getClass().getDeclaredField("tasks");
                        tasksField.setAccessible(true);
                        Object tasks = tasksField.get(job);
                        
                        if (tasks instanceof java.util.Map) {
                            java.util.Map<?, ?> tasksMap = (java.util.Map<?, ?>) tasks;
                            
                            for (Object pattern : tasksMap.keySet()) {
                                if (pattern != null) {
                                    if (!isSelectedCpu || targetKey == null || isPatternOutputsItem(pattern, targetKey)) {
                                        relevantPatterns.add(pattern);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            
            try {
                Method getWaitingTasksMethod = cluster.getClass().getMethod("getWaitingTasks");
                Iterable<?> waitingTasks = (Iterable<?>) getWaitingTasksMethod.invoke(cluster);
                
                if (waitingTasks != null) {
                    for (Object task : waitingTasks) {
                        try {
                            Method getPatternMethod = task.getClass().getMethod("getPattern");
                            Object pattern = getPatternMethod.invoke(task);
                            if (pattern != null) {
                                relevantPatterns.add(pattern);
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
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
                                Level level = getProviderLevel(provider, defaultLevel);
                                positions.add(new ProviderLocation(level.dimension(), pos));
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error finding providers from cluster for target: {}", e.getMessage());
        }
        
        return positions;
    }
    
    private static boolean isPatternOutputsItem(Object pattern, Object targetKey) {
        try {
            try {
                Method getPrimaryOutputMethod = pattern.getClass().getMethod("getPrimaryOutput");
                Object primaryOutput = getPrimaryOutputMethod.invoke(pattern);
                
                if (primaryOutput != null) {
                    Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
                    if (genericStackClass.isInstance(primaryOutput)) {
                        Method whatMethod = genericStackClass.getMethod("what");
                        Object outputKey = whatMethod.invoke(primaryOutput);
                        
                        if (outputKey != null && outputKey.equals(targetKey)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }
            
            try {
                Method getOutputsMethod = pattern.getClass().getMethod("getOutputs");
                Object outputs = getOutputsMethod.invoke(pattern);
                
                if (outputs != null && outputs instanceof Object[]) {
                    Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
                    for (Object output : (Object[]) outputs) {
                        if (genericStackClass.isInstance(output)) {
                            Method whatMethod = genericStackClass.getMethod("what");
                            Object outputKey = whatMethod.invoke(output);
                            
                            if (outputKey != null && outputKey.equals(targetKey)) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        } catch (Exception e) {
            // ignore
        }
        
        return false;
    }
    
    private static Set<ProviderLocation> findPatternProvidersForKey(Object grid, Object targetKey, Level defaultLevel) {
        Set<ProviderLocation> positions = new HashSet<>();
        
        try {
            Object craftingService = invokeMethod(grid, "getCraftingService", Object.class);
            if (craftingService == null) {
                return positions;
            }
            
            Class<?> patternDetailsClass = Class.forName("appeng.api.crafting.IPatternDetails");
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            
            Method getCraftingForMethod = craftingService.getClass().getMethod("getCraftingFor", aeKeyClass);
            Iterable<?> patterns = (Iterable<?>) getCraftingForMethod.invoke(craftingService, targetKey);
            
            if (patterns == null) {
                return positions;
            }
            
            for (Object pattern : patterns) {
                try {
                    Method getProvidersMethod = craftingService.getClass().getMethod("getProviders", patternDetailsClass);
                    Iterable<?> providers = (Iterable<?>) getProvidersMethod.invoke(craftingService, pattern);
                    
                    if (providers != null) {
                        for (Object provider : providers) {
                            BlockPos pos = getProviderPosition(provider);
                            if (pos != null) {
                                Level level = getProviderLevel(provider, defaultLevel);
                                positions.add(new ProviderLocation(level.dimension(), pos));
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error finding pattern provider positions", e);
        }
        
        return positions;
    }
    
    private static Set<ProviderLocation> findAllActivePatternProviders(Object grid, Object menu, Level defaultLevel) {
        Set<ProviderLocation> positions = new HashSet<>();
        
        try {
            Object craftingService = invokeMethod(grid, "getCraftingService", Object.class);
            if (craftingService == null) {
                return positions;
            }
            
            try {
                Method getJobsMethod = craftingService.getClass().getMethod("getJobs");
                Iterable<?> jobs = (Iterable<?>) getJobsMethod.invoke(craftingService);
                
                if (jobs != null) {
                    for (Object job : jobs) {
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
                                            Level level = getProviderLevel(provider, defaultLevel);
                                            positions.add(new ProviderLocation(level.dimension(), pos));
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
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
                    return grid;
                }
            } catch (Exception e) {
                // ignore
            }
            
            Object cpu = findFieldByTypeName(menu, "CraftingCPU");
            if (cpu != null) {
                Object grid = invokeMethod(cpu, "getGrid", Object.class);
                if (grid != null) {
                    return grid;
                }
            }
            
            Object cluster = findFieldByTypeName(menu, "Cluster");
            if (cluster != null) {
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
                            return value;
                        }
                        if (field.getName().contains(typeName)) {
                            return value;
                        }
                    }
                } catch (IllegalAccessException e) {
                    AE2CraftingLens.LOGGER.debug("Cannot access field {} on {}", 
                        field.getName(), obj.getClass().getSimpleName());
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private static <T> T invokeMethod(Object obj, String methodName, Class<T> returnType) {
        if (obj == null) return null;
        
        for (Method method : obj.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                try {
                    Object result = method.invoke(obj);
                    if (returnType.isInstance(result)) {
                        return (T) result;
                    }
                } catch (IllegalAccessException e) {
                    AE2CraftingLens.LOGGER.debug("Cannot access method {} on {}", 
                        methodName, obj.getClass().getSimpleName());
                } catch (InvocationTargetException e) {
                    AE2CraftingLens.LOGGER.debug("Method {} threw exception on {}", 
                        methodName, obj.getClass().getSimpleName(), e.getCause());
                }
            }
        }
        return null;
    }

    private static Level getProviderLevel(Object provider, Level defaultLevel) {
        if (provider == null) return defaultLevel;

        try {
            Level level = invokeMethod(provider, "getLevel", Level.class);
            if (level != null) return level;

            level = invokeMethod(provider, "level", Level.class);
            if (level != null) return level;

            Object blockEntity = invokeMethod(provider, "getBlockEntity", Object.class);
            if (blockEntity != null) {
                level = invokeMethod(blockEntity, "getLevel", Level.class);
                if (level != null) return level;
            }

            Object host = invokeMethod(provider, "getHost", Object.class);
            if (host != null && host != provider) {
                return getProviderLevel(host, defaultLevel);
            }

            for (Field field : provider.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType().isAssignableFrom(Level.class)) {
                    Object val = field.get(provider);
                    if (val != null) return (Level) val;
                }
                if (field.getName().equals("host")) {
                    Object val = field.get(provider);
                    if (val != null && val != provider) {
                         Level l = getProviderLevel(val, null); 
                         if (l != null) return l;
                    }
                }
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Failed to get provider level", e);
        }
        return defaultLevel;
    }
    
    private static class StandardAE2ProviderStrategy implements ProviderStrategy {
        @Override
        public boolean canHandle(Object provider) {
            String className = provider.getClass().getName();
            return className.contains("appeng") && 
                   (className.contains("PatternProvider") || 
                    className.contains("patternprovider") ||
                    className.contains("PatternProviderLogic"));
        }
        
        @Override
        public BlockPos extractPosition(Object provider) {
            AE2CraftingLens.LOGGER.info("StandardAE2: Trying to extract position from {}", provider.getClass().getName());
            
            // Try direct getBlockPos first
            BlockPos pos = invokeMethod(provider, "getBlockPos", BlockPos.class);
            if (pos != null) {
                AE2CraftingLens.LOGGER.info("StandardAE2: Got position directly: {}", pos);
                return pos;
            }
            
            // Check if it's a Part (mounted on cable)
            try {
                Class<?> iPartClass = Class.forName("appeng.parts.IPart");
                if (iPartClass.isInstance(provider)) {
                    AE2CraftingLens.LOGGER.info("StandardAE2: Provider is IPart, getting host");
                    Object host = invokeMethod(provider, "getHost", Object.class);
                    if (host != null) {
                        pos = extractPositionFromHost(host);
                        if (pos != null) {
                            AE2CraftingLens.LOGGER.info("StandardAE2: Got position from IPart host: {}", pos);
                            return pos;
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                // IPart class not found, continue
            }
            
            // Try to get host from provider
            Object host = getHostFromProvider(provider);
            if (host != null) {
                AE2CraftingLens.LOGGER.info("StandardAE2: Got host, extracting position");
                pos = extractPositionFromHost(host);
                if (pos != null) {
                    AE2CraftingLens.LOGGER.info("StandardAE2: Got position from host: {}", pos);
                    return pos;
                }
            }
            
            // Check if provider itself is a BlockEntity
            try {
                Class<?> blockEntityClass = Class.forName("net.minecraft.world.level.block.entity.BlockEntity");
                if (blockEntityClass.isInstance(provider)) {
                    AE2CraftingLens.LOGGER.info("StandardAE2: Provider is BlockEntity");
                    pos = invokeMethod(provider, "getBlockPos", BlockPos.class);
                    if (pos != null) {
                        AE2CraftingLens.LOGGER.info("StandardAE2: Got position from BlockEntity: {}", pos);
                        return pos;
                    }
                }
            } catch (ClassNotFoundException e) {
                // BlockEntity class not found, continue
            }
            
            AE2CraftingLens.LOGGER.warn("StandardAE2: All methods failed to get position");
            return null;
        }
    }
    
    private static class ExtendedAEProviderStrategy implements ProviderStrategy {
        @Override
        public boolean canHandle(Object provider) {
            String className = provider.getClass().getName();
            return className.contains("ExPattern") || 
                   className.contains("ex_pattern") ||
                   className.contains("ExtendedAE") ||
                   className.contains("PartExPattern");
        }
        
        @Override
        public BlockPos extractPosition(Object provider) {
            BlockPos pos = invokeMethod(provider, "getBlockPos", BlockPos.class);
            if (pos != null) return pos;
            
            String[] hostFieldNames = {"ExPatternProviderHost", "PartExPatternProvider", 
                                       "ExPatternPart", "host"};
            for (String fieldName : hostFieldNames) {
                Object host = findFieldByTypeName(provider, fieldName);
                if (host != null) {
                    pos = extractPositionFromHost(host);
                    if (pos != null) return pos;
                }
            }
            
            String[] partFieldNames = {"part", "tile", "blockEntity"};
            for (String fieldName : partFieldNames) {
                Object part = findFieldByTypeName(provider, fieldName);
                if (part != null) {
                    pos = invokeMethod(part, "getBlockPos", BlockPos.class);
                    if (pos != null) return pos;
                }
            }
            
            return null;
        }
    }
    
    private static class AdvancedAEProviderStrategy implements ProviderStrategy {
        @Override
        public boolean canHandle(Object provider) {
            String className = provider.getClass().getName();
            return className.contains("AdvPatternProvider") ||
                   className.contains("advanced_ae");
        }
        
        @Override
        public BlockPos extractPosition(Object provider) {
            try {
                Field hostField = provider.getClass().getDeclaredField("host");
                hostField.setAccessible(true);
                Object host = hostField.get(provider);
                if (host != null) {
                    BlockPos pos = extractPositionFromHost(host);
                    if (pos != null) return pos;
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.debug("Failed to get host from AdvancedAE provider", e);
            }
            
            return extractPositionFromHost(provider);
        }
    }
    
    private static class FallbackProviderStrategy implements ProviderStrategy {
        @Override
        public boolean canHandle(Object provider) {
            return true;
        }
        
        @Override
        public BlockPos extractPosition(Object provider) {
            for (Method method : provider.getClass().getMethods()) {
                if (method.getName().equals("getBlockPos") && method.getParameterCount() == 0) {
                    try {
                        Object result = method.invoke(provider);
                        if (result instanceof BlockPos) {
                            return (BlockPos) result;
                        }
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.debug("Failed to invoke getBlockPos", e);
                    }
                }
            }
            
            Object part = findFieldByTypeName(provider, "Part");
            if (part == null) {
                part = findFieldByTypeName(provider, "part");
            }
            if (part != null) {
                BlockPos pos = invokeMethod(part, "getBlockPos", BlockPos.class);
                if (pos != null) return pos;
            }
            
            return null;
        }
    }
    
    private static BlockPos extractPositionFromHost(Object host) {
        if (host == null) {
            AE2CraftingLens.LOGGER.warn("extractPositionFromHost: host is null");
            return null;
        }
        
        String hostClassName = host.getClass().getName();
        AE2CraftingLens.LOGGER.info("extractPositionFromHost: host class is {}", hostClassName);
        
        // Log all interfaces of the host
        Class<?>[] interfaces = host.getClass().getInterfaces();
        StringBuilder interfaceNames = new StringBuilder();
        for (Class<?> iface : interfaces) {
            interfaceNames.append(iface.getName()).append(", ");
        }
        AE2CraftingLens.LOGGER.info("extractPositionFromHost: host interfaces: {}", interfaceNames.toString());
        
        // Try 1: Check if it's a BlockEntity
        try {
            Class<?> blockEntityClass = Class.forName("net.minecraft.world.level.block.entity.BlockEntity");
            if (blockEntityClass.isInstance(host)) {
                AE2CraftingLens.LOGGER.info("extractPositionFromHost: Host is BlockEntity, trying getBlockPos");
                BlockPos pos = invokeMethod(host, "getBlockPos", BlockPos.class);
                if (pos != null) {
                    AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got position from BlockEntity: {}", pos);
                    return pos;
                }
                AE2CraftingLens.LOGGER.warn("extractPositionFromHost: BlockEntity.getBlockPos() returned null");
            } else {
                AE2CraftingLens.LOGGER.info("extractPositionFromHost: Host is not BlockEntity");
            }
        } catch (ClassNotFoundException e) {
            AE2CraftingLens.LOGGER.info("extractPositionFromHost: BlockEntity class not found");
        }
        
        // Try 2: getBlockEntity()
        Object blockEntity = invokeMethod(host, "getBlockEntity", Object.class);
        if (blockEntity != null) {
            AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got blockEntity: {}", blockEntity.getClass().getName());
            BlockPos pos = invokeMethod(blockEntity, "getBlockPos", BlockPos.class);
            if (pos != null) {
                AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got position from blockEntity: {}", pos);
                return pos;
            }
            AE2CraftingLens.LOGGER.warn("extractPositionFromHost: blockEntity.getBlockPos() returned null");
        } else {
            AE2CraftingLens.LOGGER.info("extractPositionFromHost: getBlockEntity() returned null");
        }
        
        // Try 3: Direct getBlockPos() on host
        BlockPos pos = invokeMethod(host, "getBlockPos", BlockPos.class);
        if (pos != null) {
            AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got position directly from host: {}", pos);
            return pos;
        }
        AE2CraftingLens.LOGGER.info("extractPositionFromHost: host.getBlockPos() returned null");
        
        // Try 4: Check if host is IPartHost and use getLocation()
        try {
            Class<?> iPartHostClass = Class.forName("appeng.parts.IPartHost");
            if (iPartHostClass.isInstance(host)) {
                AE2CraftingLens.LOGGER.info("extractPositionFromHost: Host is IPartHost, using getLocation()");
                Object location = invokeMethod(host, "getLocation", Object.class);
                if (location != null) {
                    AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got location: {}", location.getClass().getName());
                    pos = invokeMethod(location, "getPos", BlockPos.class);
                    if (pos != null) {
                        AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got position from location.getPos(): {}", pos);
                        return pos;
                    }
                    AE2CraftingLens.LOGGER.warn("extractPositionFromHost: location.getPos() returned null");
                } else {
                    AE2CraftingLens.LOGGER.warn("extractPositionFromHost: getLocation() returned null");
                }
            } else {
                AE2CraftingLens.LOGGER.info("extractPositionFromHost: Host is not IPartHost");
            }
        } catch (ClassNotFoundException e) {
            AE2CraftingLens.LOGGER.info("extractPositionFromHost: IPartHost class not found");
        }
        
        // Try 5: Check for IPatternProviderHost specific methods
        try {
            Class<?> patternProviderHostClass = Class.forName("appeng.helpers.patternprovider.IPatternProviderHost");
            if (patternProviderHostClass.isInstance(host)) {
                AE2CraftingLens.LOGGER.info("extractPositionFromHost: Host is IPatternProviderHost");
                
                // Try getLocation() method which should be available on IPatternProviderHost
                Object location = invokeMethod(host, "getLocation", Object.class);
                if (location != null) {
                    AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got location from IPatternProviderHost: {}", location.getClass().getName());
                    pos = invokeMethod(location, "getPos", BlockPos.class);
                    if (pos != null) {
                        AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got position from IPatternProviderHost location.getPos(): {}", pos);
                        return pos;
                    }
                    AE2CraftingLens.LOGGER.warn("extractPositionFromHost: location.getPos() returned null");
                } else {
                    AE2CraftingLens.LOGGER.warn("extractPositionFromHost: IPatternProviderHost.getLocation() returned null");
                }
            } else {
                AE2CraftingLens.LOGGER.info("extractPositionFromHost: Host is not IPatternProviderHost");
            }
        } catch (ClassNotFoundException e) {
            AE2CraftingLens.LOGGER.info("extractPositionFromHost: IPatternProviderHost class not found");
        }
        
        // Try 6: Check if host has getHost() method (nested host)
        try {
            Method getHostMethod = host.getClass().getMethod("getHost");
            Object partHost = getHostMethod.invoke(host);
            if (partHost != null) {
                AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got nested host: {}", partHost.getClass().getName());
                pos = extractPositionFromHost(partHost); // Recursive call
                if (pos != null) {
                    AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got position from nested host: {}", pos);
                    return pos;
                }
                AE2CraftingLens.LOGGER.warn("extractPositionFromHost: nested host extraction returned null");
            } else {
                AE2CraftingLens.LOGGER.info("extractPositionFromHost: getHost() returned null");
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.info("extractPositionFromHost: Failed to get nested host: {}", e.getMessage());
        }
        
        // Try 7: Check for getGridNode() method and get location from grid node
        try {
            Object gridNode = invokeMethod(host, "getGridNode", Object.class);
            if (gridNode != null) {
                AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got gridNode: {}", gridNode.getClass().getName());
                // Try to get location from grid node
                Object gridHost = invokeMethod(gridNode, "getGridHost", Object.class);
                if (gridHost != null) {
                    AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got gridHost: {}", gridHost.getClass().getName());
                    pos = extractPositionFromHost(gridHost); // Recursive call
                    if (pos != null) {
                        AE2CraftingLens.LOGGER.info("extractPositionFromHost: Got position from gridHost: {}", pos);
                        return pos;
                    }
                }
            } else {
                AE2CraftingLens.LOGGER.info("extractPositionFromHost: getGridNode() returned null");
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.info("extractPositionFromHost: Failed to get gridNode: {}", e.getMessage());
        }
        
        AE2CraftingLens.LOGGER.warn("extractPositionFromHost: All methods failed to get position from host: {}", hostClassName);
        return null;
    }
    
    private static Object getHostFromProvider(Object provider) {
        Object host = invokeMethod(provider, "getHost", Object.class);
        if (host != null) return host;
        
        try {
            Field hostField = provider.getClass().getDeclaredField("host");
            hostField.setAccessible(true);
            host = hostField.get(provider);
            if (host != null) return host;
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Failed to get host field", e);
        }
        
        String[] hostTypeNames = {"PatternProviderLogicHost", "PatternProviderBlockEntity",
                                  "AdvPatternProviderLogicHost", "AdvPatternProviderEntity",
                                  "AdvPatternProviderPart"};
        for (String typeName : hostTypeNames) {
            host = findFieldByTypeName(provider, typeName);
            if (host != null) return host;
        }
        
        return null;
    }
    
    private static BlockPos getProviderPosition(Object provider) {
        if (provider == null) {
            AE2CraftingLens.LOGGER.warn("Provider is null");
            return null;
        }
        
        String providerClassName = provider.getClass().getName();
        AE2CraftingLens.LOGGER.info("Getting position for provider: {}", providerClassName);
        
        // Try each strategy in order
        for (ProviderStrategy strategy : PROVIDER_STRATEGIES) {
            if (strategy.canHandle(provider)) {
                AE2CraftingLens.LOGGER.info("Using strategy: {}", strategy.getClass().getSimpleName());
                BlockPos pos = strategy.extractPosition(provider);
                if (pos != null) {
                    AE2CraftingLens.LOGGER.info("Found position: {}", pos);
                    return pos;
                }
                AE2CraftingLens.LOGGER.info("Strategy {} failed to find position", strategy.getClass().getSimpleName());
            }
        }
        
        AE2CraftingLens.LOGGER.warn("Could not find position for provider: {}", providerClassName);
        return null;
    }
}
