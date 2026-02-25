package com.ae2craftinglens.mod.network;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PatternProviderRequestHandler {

    public static void handle(RequestPatternProvidersPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        Object targetKey = packet.what();
        int rowIndex = packet.rowIndex();
        
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
            
            Set<BlockPos> providerPositions = new HashSet<>();

            if (containerClassName.contains("CraftingStatusMenu")) {
                providerPositions = findProvidersFromCurrentCraftingJob(grid, player.containerMenu, craftingService, targetKey, rowIndex);
            }

            if (providerPositions.isEmpty()) {
                providerPositions = targetKey == null ?
                    findAllActivePatternProviders(grid, player.containerMenu) :
                    findPatternProvidersForKey(grid, targetKey);
            }
            
            PatternProviderResponsePacket response = new PatternProviderResponsePacket(providerPositions);
            context.reply(response);
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error handling pattern provider request", e);
        }
    }
    
    private static Set<BlockPos> findProvidersFromCurrentCraftingJob(Object grid, Object menu, Object craftingService, Object targetKey, int rowIndex) {
        Set<BlockPos> positions = new HashSet<>();

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
                                    positions.add(pos);
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
                    Set<BlockPos> clusterProviders = findProvidersFromClusterForTarget(cluster, craftingService, targetKey, true);
                    
                    if (!clusterProviders.isEmpty()) {
                        return clusterProviders;
                    }
                }
                
                Object cluster2 = deepFindCraftingCPUCluster(menu);
                
                if (cluster2 != null) {
                    Set<BlockPos> clusterProviders = findProvidersFromClusterForTarget(cluster2, craftingService, targetKey, true);
                    
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
    
    private static Set<BlockPos> findProvidersFromClusterForTarget(Object cluster, Object craftingService, Object targetKey, boolean isSelectedCpu) {
        Set<BlockPos> positions = new HashSet<>();
        
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
                craftingLogic = findFieldByTypeName(cluster, "CraftingCpuLogic");
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
                                positions.add(pos);
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
    
    private static Set<BlockPos> findPatternProvidersForKey(Object grid, Object targetKey) {
        Set<BlockPos> positions = new HashSet<>();
        
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
                                positions.add(pos);
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
    
    private static Set<BlockPos> findAllActivePatternProviders(Object grid, Object menu) {
        Set<BlockPos> positions = new HashSet<>();
        
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
                                            positions.add(pos);
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
                    // ignore
                }
            }
            clazz = clazz.getSuperclass();
        }
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
                    // ignore
                }
            }
        }
        return null;
    }
    
    private static BlockPos getProviderPosition(Object provider) {
        try {
            String providerClassName = provider.getClass().getName();
            
            boolean isExtendedAEProvider = providerClassName.contains("ExPattern") || 
                                         providerClassName.contains("ex_pattern") ||
                                         providerClassName.contains("ExtendedAE") ||
                                         providerClassName.contains("PartExPattern");
            
            boolean isAdvancedAEProvider = providerClassName.contains("AdvPatternProvider") ||
                                          providerClassName.contains("advanced_ae");
            
            Object host = null;
            
            if (isAdvancedAEProvider) {
                try {
                    Class<?> advPatternProviderLogicHostClass = Class.forName("net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost");
                    if (advPatternProviderLogicHostClass.isInstance(provider)) {
                        try {
                            Field hostField = provider.getClass().getDeclaredField("host");
                            hostField.setAccessible(true);
                            host = hostField.get(provider);
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                } catch (ClassNotFoundException e) {
                    // ignore
                }
            }
            
            if (host == null) {
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
                        host = provider;
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            
            if (host == null) {
                try {
                    Method getHostMethod = provider.getClass().getMethod("getHost");
                    host = getHostMethod.invoke(provider);
                } catch (Exception e) {
                    // ignore
                }
            }
            
            if (host == null) {
                try {
                    Field hostField = provider.getClass().getDeclaredField("host");
                    hostField.setAccessible(true);
                    host = hostField.get(provider);
                } catch (Exception e) {
                    // ignore
                }
            }
            
            if (host == null) {
                host = findFieldByTypeName(provider, "PatternProviderLogicHost");
                if (host == null) {
                    host = findFieldByTypeName(provider, "PatternProviderBlockEntity");
                }
                if (host == null) {
                    host = findFieldByTypeName(provider, "AdvPatternProviderLogicHost");
                }
                if (host == null) {
                    host = findFieldByTypeName(provider, "AdvPatternProviderEntity");
                }
                if (host == null) {
                    host = findFieldByTypeName(provider, "AdvPatternProviderPart");
                }
            }
            
            if (isExtendedAEProvider && host == null) {
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
            
            if (host != null) {
                Object blockEntity = invokeMethod(host, "getBlockEntity", Object.class);
                if (blockEntity != null) {
                    BlockPos pos = invokeMethod(blockEntity, "getBlockPos", BlockPos.class);
                    if (pos != null) {
                        return pos;
                    }
                }
                
                BlockPos pos = invokeMethod(host, "getBlockPos", BlockPos.class);
                if (pos != null) {
                    return pos;
                }
                
                try {
                    Method getHostMethod = host.getClass().getMethod("getHost");
                    Object partHost = getHostMethod.invoke(host);
                    if (partHost != null) {
                        BlockPos partPos = invokeMethod(partHost, "getBlockPos", BlockPos.class);
                        if (partPos != null) {
                            return partPos;
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            
            for (Method method : provider.getClass().getMethods()) {
                if (method.getName().equals("getBlockPos") && method.getParameterCount() == 0) {
                    try {
                        Object result = method.invoke(provider);
                        if (result instanceof BlockPos) {
                            return (BlockPos) result;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            
            try {
                Object part = findFieldByTypeName(provider, "Part");
                if (part == null) {
                    part = findFieldByTypeName(provider, "part");
                }
                if (part != null) {
                    BlockPos pos = invokeMethod(part, "getBlockPos", BlockPos.class);
                    if (pos != null) {
                        return pos;
                    }
                }
            } catch (Exception e) {
                // ignore
            }
            
            if (isExtendedAEProvider) {
                try {
                    Object extendedHost = findFieldByTypeName(provider, "ExPatternProviderHost");
                    if (extendedHost == null) {
                        extendedHost = findFieldByTypeName(provider, "PartExPatternProvider");
                    }
                    if (extendedHost == null) {
                        extendedHost = findFieldByTypeName(provider, "ExPatternPart");
                    }
                    
                    if (extendedHost != null) {
                        BlockPos pos = invokeMethod(extendedHost, "getBlockPos", BlockPos.class);
                        if (pos != null) {
                            return pos;
                        }
                        
                        Object blockEntity = invokeMethod(extendedHost, "getBlockEntity", Object.class);
                        if (blockEntity != null) {
                            pos = invokeMethod(blockEntity, "getBlockPos", BlockPos.class);
                            if (pos != null) {
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
                            return pos;
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            
            AE2CraftingLens.LOGGER.warn("Could not find position for provider: {}", providerClassName);
            
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
