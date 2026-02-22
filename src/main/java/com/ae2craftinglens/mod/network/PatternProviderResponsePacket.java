package com.ae2craftinglens.mod.network;

import java.util.HashSet;
import java.util.Set;

import com.ae2craftinglens.mod.AE2CraftingLens;
import com.ae2craftinglens.mod.PatternProviderHighlightManager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PatternProviderResponsePacket(Set<BlockPos> positions) implements CustomPacketPayload {
    
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
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            
            PatternProviderHighlightManager manager = PatternProviderHighlightManager.getInstance();
            
            if (packet.positions().isEmpty()) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.ae2craftinglens.no_providers_found"),
                        true
                );
                return;
            }
            
            // 添加高亮显示
            for (BlockPos pos : packet.positions()) {
                manager.addHighlightedProvider(mc.level, pos);
            }
            
            // 显示详细信息
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.ae2craftinglens.highlighted_providers", 
                            packet.positions().size()),
                    true
            );
            
            // 显示每个供应器的详细信息
            int index = 1;
            for (BlockPos pos : packet.positions()) {
                try {
                    // 获取维度信息
                    String dimension = mc.level.dimension().location().toString();
                    
                    // 计算距离
                    double distance = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    distance = Math.sqrt(distance);
                    
                    // 创建可点击的坐标组件
                    net.minecraft.network.chat.MutableComponent message = net.minecraft.network.chat.Component.literal("Provider " + index + ": ");
                    
                    // 添加维度信息
                    net.minecraft.network.chat.MutableComponent dimComponent = net.minecraft.network.chat.Component.literal(dimension)
                            .withStyle(style -> style
                                    .withColor(net.minecraft.network.chat.TextColor.fromRgb(0x00FFFF))
                                    .withUnderlined(true)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                            "/execute in " + dimension + " run tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                    ))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                            net.minecraft.network.chat.Component.literal("Click to teleport to this dimension")
                                    ))
                            );
                    message.append(dimComponent);
                    message.append(net.minecraft.network.chat.Component.literal(" at "));
                    
                    // 添加坐标信息
                    net.minecraft.network.chat.MutableComponent posComponent = net.minecraft.network.chat.Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                            .withStyle(style -> style
                                    .withColor(net.minecraft.network.chat.TextColor.fromRgb(0x00FF00))
                                    .withUnderlined(true)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                            "/tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                    ))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                            net.minecraft.network.chat.Component.literal("Click to teleport to this position")
                                    ))
                            );
                    message.append(posComponent);
                    message.append(net.minecraft.network.chat.Component.literal(" ("));
                    
                    // 添加距离信息
                    net.minecraft.network.chat.MutableComponent distanceComponent = net.minecraft.network.chat.Component.literal(String.format("%.1f blocks", distance))
                            .withStyle(style -> style
                                    .withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFFF00))
                            );
                    message.append(distanceComponent);
                    message.append(net.minecraft.network.chat.Component.literal(")"));
                    
                    mc.player.displayClientMessage(message, false);
                    index++;
                } catch (Exception e) {
                    AE2CraftingLens.LOGGER.error("Error creating provider message", e);
                }
            }
        });
    }
}
