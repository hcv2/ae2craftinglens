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
            AE2CraftingLens.LOGGER.warn("Received null AEKey in request");
            return;
        }
        
        AE2CraftingLens.LOGGER.info("Looking for pattern providers for: {}", targetKey);
        
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
            AE2CraftingLens.LOGGER.info("Attempting to find CraftingCPUCluster");
            Object cluster = findFieldByTypeName(player.containerMenu, "CraftingCPUCluster");
            if (cluster == null) {
                AE2CraftingLens.LOGGER.warn("Could not find CraftingCPUCluster in menu");
                return;
            }
            AE2CraftingLens.LOGGER.info("Found CraftingCPUCluster: {}", cluster);
            
            AE2CraftingLens.LOGGER.info("Attempting to get grid");
            Object grid = invokeMethod(cluster, "getGrid", Object.class);
            if (grid == null) {
                AE2CraftingLens.LOGGER.warn("Could not get grid");
                return;
            }
            AE2CraftingLens.LOGGER.info("Found grid: {}", grid);
            
            AE2CraftingLens.LOGGER.info("Attempting to find pattern providers");
            Set<BlockPos> providerPositions = findPatternProvidersForKey(grid, targetKey);
            AE2CraftingLens.LOGGER.info("Found {} pattern provider positions for {}", providerPositions.size(), targetKey);
            
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
            
            // 方法1: 尝试找到 PatternProviderLogicHost 或 PatternProviderBlockEntity
            Object host = findFieldByTypeName(provider, "PatternProviderLogicHost");
            if (host == null) {
                host = findFieldByTypeName(provider, "PatternProviderBlockEntity");
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
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Error getting provider position: {}", e.getMessage());
        }
        return null;
    }
}
