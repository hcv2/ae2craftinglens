package com.ae2craftinglens.mod.network;

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
        // 只在客户端处理
        if (!context.flow().isClientbound()) {
            return;
        }
        
        context.enqueueWork(() -> {
            try {
                // 动态加载Minecraft类，避免服务器端加载客户端类
                Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
                Method getInstanceMethod = minecraftClass.getMethod("getInstance");
                Object minecraft = getInstanceMethod.invoke(null);
                
                // 获取player
                Method getPlayerMethod = minecraftClass.getMethod("getPlayer");
                Object player = getPlayerMethod.invoke(minecraft);
                if (player == null) return;
                
                // 获取level
                Method getLevelMethod = minecraftClass.getMethod("level");
                Object level = getLevelMethod.invoke(minecraft);
                if (level == null) return;
                
                PatternProviderHighlightManager manager = PatternProviderHighlightManager.getInstance();
                
                if (packet.positions().isEmpty()) {
                    // 显示无供应器消息
                    Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                    Class<?> translatableClass = Class.forName("net.minecraft.network.chat.TranslatableComponent");
                    Object message = translatableClass.getConstructor(String.class).newInstance("message.ae2craftinglens.no_providers_found");
                    
                    Method displayClientMessageMethod = player.getClass().getMethod("displayClientMessage", componentClass, boolean.class);
                    displayClientMessageMethod.invoke(player, message, true);
                    return;
                }
                
                // 添加高亮显示
                for (BlockPos pos : packet.positions()) {
                    manager.addHighlightedProvider((net.minecraft.world.level.Level) level, pos);
                }
                
                // 显示详细信息
                Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
                Class<?> translatableClass = Class.forName("net.minecraft.network.chat.TranslatableComponent");
                Object message = translatableClass.getConstructor(String.class, Object[].class).newInstance(
                        "message.ae2craftinglens.highlighted_providers", 
                        new Object[]{packet.positions().size()}
                );
                
                Method displayClientMessageMethod = player.getClass().getMethod("displayClientMessage", componentClass, boolean.class);
                displayClientMessageMethod.invoke(player, message, true);
                
                // 显示每个供应器的详细信息
                int index = 1;
                for (BlockPos pos : packet.positions()) {
                    try {
                        // 获取维度信息
                        Method dimensionMethod = level.getClass().getMethod("dimension");
                        Object dimension = dimensionMethod.invoke(level);
                        Method locationMethod = dimension.getClass().getMethod("location");
                        Object location = locationMethod.invoke(dimension);
                        String dimensionStr = location.toString();
                        
                        // 计算距离
                        Method distanceToSqrMethod = player.getClass().getMethod("distanceToSqr", double.class, double.class, double.class);
                        double distanceSqr = (double) distanceToSqrMethod.invoke(player, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        double distance = Math.sqrt(distanceSqr);
                        
                        // 创建可点击的坐标组件
                        Class<?> literalClass = Class.forName("net.minecraft.network.chat.LiteralComponent");
                        Object baseMessage = literalClass.getConstructor(String.class).newInstance("Provider " + index + ": ");
                        
                        // 添加维度信息
                        Object dimComponent = literalClass.getConstructor(String.class).newInstance(dimensionStr);
                        Method withStyleMethod = componentClass.getMethod("withStyle", net.minecraft.network.chat.Style.class);
                        
                        // 创建样式
                        Class<?> styleClass = Class.forName("net.minecraft.network.chat.Style");
                        Class<?> textColorClass = Class.forName("net.minecraft.network.chat.TextColor");
                        Method fromRgbMethod = textColorClass.getMethod("fromRgb", int.class);
                        Object textColor = fromRgbMethod.invoke(null, 0x00FFFF);
                        
                        Method styleBuilderMethod = styleClass.getMethod("copy");
                        Object style = styleBuilderMethod.invoke(null);
                        
                        // 设置颜色
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
                        Object hoverText = literalClass.getConstructor(String.class).newInstance("Click to teleport to this dimension");
                        Object hoverEvent = hoverEventClass.getConstructor(hoverEventActionClass, componentClass).newInstance(
                                showTextAction, 
                                hoverText
                        );
                        
                        // 设置悬停事件
                        Method withHoverEventMethod = styleClass.getMethod("withHoverEvent", hoverEventClass);
                        style = withHoverEventMethod.invoke(style, hoverEvent);
                        
                        // 应用样式
                        dimComponent = withStyleMethod.invoke(dimComponent, style);
                        
                        // 追加到消息
                        Method appendMethod = componentClass.getMethod("append", componentClass);
                        baseMessage = appendMethod.invoke(baseMessage, dimComponent);
                        baseMessage = appendMethod.invoke(baseMessage, literalClass.getConstructor(String.class).newInstance(" at "));
                        
                        // 添加坐标信息
                        Object posComponent = literalClass.getConstructor(String.class).newInstance(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                        
                        // 创建坐标样式
                        style = styleBuilderMethod.invoke(null);
                        style = withColorMethod.invoke(style, fromRgbMethod.invoke(null, 0x00FF00));
                        style = withUnderlinedMethod.invoke(style, true);
                        
                        // 设置点击事件
                        clickEvent = clickEventClass.getConstructor(clickEventActionClass, String.class).newInstance(
                                runCommandAction, 
                                "/tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                        );
                        style = withClickEventMethod.invoke(style, clickEvent);
                        
                        // 设置悬停事件
                        hoverText = literalClass.getConstructor(String.class).newInstance("Click to teleport to this position");
                        hoverEvent = hoverEventClass.getConstructor(hoverEventActionClass, componentClass).newInstance(
                                showTextAction, 
                                hoverText
                        );
                        style = withHoverEventMethod.invoke(style, hoverEvent);
                        
                        // 应用样式
                        posComponent = withStyleMethod.invoke(posComponent, style);
                        baseMessage = appendMethod.invoke(baseMessage, posComponent);
                        baseMessage = appendMethod.invoke(baseMessage, literalClass.getConstructor(String.class).newInstance(" ("));
                        
                        // 添加距离信息
                        Object distanceComponent = literalClass.getConstructor(String.class).newInstance(String.format("%.1f blocks", distance));
                        style = styleBuilderMethod.invoke(null);
                        style = withColorMethod.invoke(style, fromRgbMethod.invoke(null, 0xFFFF00));
                        distanceComponent = withStyleMethod.invoke(distanceComponent, style);
                        baseMessage = appendMethod.invoke(baseMessage, distanceComponent);
                        baseMessage = appendMethod.invoke(baseMessage, literalClass.getConstructor(String.class).newInstance(")"));
                        
                        // 显示消息
                        displayClientMessageMethod.invoke(player, baseMessage, false);
                        index++;
                    } catch (Exception e) {
                        AE2CraftingLens.LOGGER.error("Error creating provider message", e);
                    }
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.error("Error handling pattern provider response", e);
            }
        });
    }
}
