package com.ae2craftinglens.mod.network;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import com.ae2craftinglens.mod.AE2CraftingLens;
import com.ae2craftinglens.mod.PatternProviderHighlightManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PatternProviderResponsePacket(Set<BlockPos> positions) implements CustomPacketPayload {
    
    @SuppressWarnings("null")
    public static final Type<PatternProviderResponsePacket> TYPE = 
            new Type<>(ResourceLocation.fromNamespaceAndPath(AE2CraftingLens.MODID, "pattern_provider_response"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, PatternProviderResponsePacket> STREAM_CODEC = 
            StreamCodec.of(
                    PatternProviderResponsePacket::encode,
                    PatternProviderResponsePacket::decode
            );
    
    private static void encode(RegistryFriendlyByteBuf buffer, PatternProviderResponsePacket packet) {
        buffer.writeInt(packet.positions().size());
        for (BlockPos pos : packet.positions()) {
            buffer.writeInt(pos.getX());
            buffer.writeInt(pos.getY());
            buffer.writeInt(pos.getZ());
        }
    }
    
    private static PatternProviderResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readInt();
        Set<BlockPos> positions = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int x = buffer.readInt();
            int y = buffer.readInt();
            int z = buffer.readInt();
            positions.add(new BlockPos(x, y, z));
        }
        return new PatternProviderResponsePacket(positions);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(PatternProviderResponsePacket packet, IPayloadContext context) {
        AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Client received pattern provider response ===");
        
        // 只在客户端处理
        if (!context.flow().isClientbound()) {
            AE2CraftingLens.LOGGER.info("Not a clientbound packet, skipping");
            return;
        }
        
        context.enqueueWork(() -> {
            try {
                AE2CraftingLens.LOGGER.info("Processing response packet with {} positions", packet.positions().size());
                
                // 动态加载Minecraft类，避免服务器端加载客户端类
                Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
                Method getInstanceMethod = minecraftClass.getMethod("getInstance");
                Object minecraft = getInstanceMethod.invoke(null);
                AE2CraftingLens.LOGGER.info("Found Minecraft instance: {}", minecraft);
                
                // 获取player - 在1.21.1中，player是字段而不是方法
                Field playerField = minecraftClass.getDeclaredField("player");
                playerField.setAccessible(true);
                Object player = playerField.get(minecraft);
                if (player == null) {
                    AE2CraftingLens.LOGGER.warn("Player is null, skipping");
                    return;
                }
                AE2CraftingLens.LOGGER.info("Found player: {}", player);
                
                // 获取level - 在1.21.1中，level是字段而不是方法
                Field levelField = minecraftClass.getDeclaredField("level");
                levelField.setAccessible(true);
                Object level = levelField.get(minecraft);
                if (level == null) {
                    AE2CraftingLens.LOGGER.warn("Level is null, skipping");
                    return;
                }
                AE2CraftingLens.LOGGER.info("Found level: {}", level);
                
                PatternProviderHighlightManager manager = PatternProviderHighlightManager.getInstance();
                AE2CraftingLens.LOGGER.info("Found highlight manager: {}", manager);
                
                if (packet.positions().isEmpty()) {
                    AE2CraftingLens.LOGGER.info("No pattern providers found");
                    // 显示无供应器消息
                    try {
                        Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                        // 使用 Component.translatable() 方法
                        Method translatableMethod = componentClass.getMethod("translatable", String.class);
                        Object message = translatableMethod.invoke(null, "message.ae2craftinglens.no_providers_found");
                        
                        Method displayClientMessageMethod = player.getClass().getMethod("displayClientMessage", componentClass, boolean.class);
                        displayClientMessageMethod.invoke(player, message, true);
                        AE2CraftingLens.LOGGER.info("No providers message displayed");
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.error("Error displaying no providers message", e);
                    }
                    return;
                }
                
                // 添加高亮显示
                AE2CraftingLens.LOGGER.info("Adding highlighted providers");
                for (BlockPos pos : packet.positions()) {
                    AE2CraftingLens.LOGGER.info("Adding highlight for provider at: {}", pos);
                    manager.addHighlightedProvider((net.minecraft.world.level.Level) level, pos);
                }
                
                // 显示详细信息
                try {
                    Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                    // 使用 Component.translatable() 方法
                    Method translatableMethod = componentClass.getMethod("translatable", String.class, Object[].class);
                    Object message = translatableMethod.invoke(null, 
                            "message.ae2craftinglens.highlighted_providers", 
                            new Object[]{packet.positions().size()}
                    );
                    
                    Method displayClientMessageMethod = player.getClass().getMethod("displayClientMessage", componentClass, boolean.class);
                    displayClientMessageMethod.invoke(player, message, true);
                    AE2CraftingLens.LOGGER.info("Highlighted providers message displayed");
                    
                    // 显示每个供应器的详细信息
                    int index = 1;
                    for (BlockPos pos : packet.positions()) {
                        try {
                            AE2CraftingLens.LOGGER.info("Processing provider {} at: {}", index, pos);
                            // 获取维度信息
                            Method dimensionMethod = level.getClass().getMethod("dimension");
                            Object dimension = dimensionMethod.invoke(level);
                            Method locationMethod = dimension.getClass().getMethod("location");
                            Object location = locationMethod.invoke(dimension);
                            String dimensionStr = location.toString();
                            AE2CraftingLens.LOGGER.info("Dimension: {}", dimensionStr);
                            
                            // 计算距离
                            Method distanceToSqrMethod = player.getClass().getMethod("distanceToSqr", double.class, double.class, double.class);
                            double distanceSqr = (double) distanceToSqrMethod.invoke(player, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                            double distance = Math.sqrt(distanceSqr);
                            AE2CraftingLens.LOGGER.info("Distance: {} blocks", distance);
                            
                            // 使用 Component.literal() 创建文本组件
                            Method literalMethod = componentClass.getMethod("literal", String.class);
                            Object baseMessage = literalMethod.invoke(null, "Provider " + index + ": ");
                            
                            // 添加维度信息
                            Object dimComponent = literalMethod.invoke(null, dimensionStr);
                            
                            // 创建样式 - 使用 Style.EMPTY.copy()
                            Class<?> styleClass = Class.forName("net.minecraft.network.chat.Style");
                            Object emptyStyle = styleClass.getField("EMPTY").get(null);
                            Method copyMethod = styleClass.getMethod("copy");
                            Object style = copyMethod.invoke(emptyStyle);
                            
                            // 设置颜色 - 使用 TextColor.fromRgb()
                            Class<?> textColorClass = Class.forName("net.minecraft.network.chat.TextColor");
                            Method fromRgbMethod = textColorClass.getMethod("fromRgb", int.class);
                            Object textColor = fromRgbMethod.invoke(null, 0x00FFFF);
                            Method withColorMethod = styleClass.getMethod("withColor", textColorClass);
                            style = withColorMethod.invoke(style, textColor);
                            
                            // 设置下划线
                            Method withUnderlinedMethod = styleClass.getMethod("withUnderlined", boolean.class);
                            style = withUnderlinedMethod.invoke(style, true);
                            
                            // 创建点击事件
                            Class<?> clickEventClass = Class.forName("net.minecraft.network.chat.ClickEvent");
                            Class<?> clickEventActionClass = Class.forName("net.minecraft.network.chat.ClickEvent$Action");
                            Object runCommandAction = clickEventActionClass.getField("RUN_COMMAND").get(null);
                            Object clickEvent = clickEventClass.getConstructor(clickEventActionClass, String.class).newInstance(
                                    runCommandAction, 
                                    "/execute in " + dimensionStr + " run tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                            );
                            
                            // 设置点击事件
                            Method withClickEventMethod = styleClass.getMethod("withClickEvent", clickEventClass);
                            style = withClickEventMethod.invoke(style, clickEvent);
                            
                            // 创建悬停事件
                            Class<?> hoverEventClass = Class.forName("net.minecraft.network.chat.HoverEvent");
                            Class<?> hoverEventActionClass = Class.forName("net.minecraft.network.chat.HoverEvent$Action");
                            Object showTextAction = hoverEventActionClass.getField("SHOW_TEXT").get(null);
                            Object hoverText = literalMethod.invoke(null, "Click to teleport to this dimension");
                            Object hoverEvent = hoverEventClass.getConstructor(hoverEventActionClass, componentClass).newInstance(
                                    showTextAction, 
                                    hoverText
                            );
                            
                            // 设置悬停事件
                            Method withHoverEventMethod = styleClass.getMethod("withHoverEvent", hoverEventClass);
                            style = withHoverEventMethod.invoke(style, hoverEvent);
                            
                            // 应用样式 - 使用 Component#withStyle
                            Method withStyleMethod = componentClass.getMethod("withStyle", styleClass);
                            dimComponent = withStyleMethod.invoke(dimComponent, style);
                            
                            // 追加到消息
                            Method appendMethod = componentClass.getMethod("append", componentClass);
                            baseMessage = appendMethod.invoke(baseMessage, dimComponent);
                            baseMessage = appendMethod.invoke(baseMessage, literalMethod.invoke(null, " at "));
                            
                            // 添加坐标信息
                            Object posComponent = literalMethod.invoke(null, pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                            
                            // 创建坐标样式
                            style = copyMethod.invoke(emptyStyle);
                            style = withColorMethod.invoke(style, fromRgbMethod.invoke(null, 0x00FF00));
                            style = withUnderlinedMethod.invoke(style, true);
                            
                            // 设置点击事件
                            clickEvent = clickEventClass.getConstructor(clickEventActionClass, String.class).newInstance(
                                    runCommandAction, 
                                    "/tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                            );
                            style = withClickEventMethod.invoke(style, clickEvent);
                            
                            // 设置悬停事件
                            hoverText = literalMethod.invoke(null, "Click to teleport to this position");
                            hoverEvent = hoverEventClass.getConstructor(hoverEventActionClass, componentClass).newInstance(
                                    showTextAction, 
                                    hoverText
                            );
                            style = withHoverEventMethod.invoke(style, hoverEvent);
                            
                            // 应用样式
                            posComponent = withStyleMethod.invoke(posComponent, style);
                            baseMessage = appendMethod.invoke(baseMessage, posComponent);
                            baseMessage = appendMethod.invoke(baseMessage, literalMethod.invoke(null, " ("));
                            
                            // 添加距离信息
                            Object distanceComponent = literalMethod.invoke(null, String.format("%.1f blocks", distance));
                            style = copyMethod.invoke(emptyStyle);
                            style = withColorMethod.invoke(style, fromRgbMethod.invoke(null, 0xFFFF00));
                            distanceComponent = withStyleMethod.invoke(distanceComponent, style);
                            baseMessage = appendMethod.invoke(baseMessage, distanceComponent);
                            baseMessage = appendMethod.invoke(baseMessage, literalMethod.invoke(null, ")"));
                            
                            // 显示消息
                            displayClientMessageMethod.invoke(player, baseMessage, false);
                            AE2CraftingLens.LOGGER.info("Provider {} message displayed", index);
                            index++;
                        } catch (Exception e) {
                            AE2CraftingLens.LOGGER.error("Error creating provider message", e);
                        }
                    }
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.error("Error displaying provider messages", e);
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.error("Error handling pattern provider response", e);
            }
            AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Client response processed ===");
        });
    }
}
