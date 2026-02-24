package com.ae2craftinglens.mod.network;

import java.util.HashSet;
import java.util.Set;

import com.ae2craftinglens.mod.AE2CraftingLens;
import com.ae2craftinglens.mod.PatternProviderHighlightManager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
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
        
        if (!context.flow().isClientbound()) {
            AE2CraftingLens.LOGGER.info("Not a clientbound packet, skipping");
            return;
        }
        
        context.enqueueWork(() -> {
            try {
                AE2CraftingLens.LOGGER.info("Processing response packet with {} positions", packet.positions().size());
                
                Minecraft mc = Minecraft.getInstance();
                var player = mc.player;
                if (player == null) {
                    AE2CraftingLens.LOGGER.warn("Player is null, skipping");
                    return;
                }
                
                Level level = mc.level;
                if (level == null) {
                    AE2CraftingLens.LOGGER.warn("Level is null, skipping");
                    return;
                }
                
                PatternProviderHighlightManager manager = PatternProviderHighlightManager.getInstance();
                
                if (packet.positions().isEmpty()) {
                    displayMessage(player, "message.ae2craftinglens.no_providers_found", null, false);
                    return;
                }
                
                for (BlockPos pos : packet.positions()) {
                    manager.addHighlightedProvider(player.getUUID(), level, pos);
                }
                
                displayMessage(player, "message.ae2craftinglens.highlighted_providers", packet.positions().size(), true);
                
                int index = 1;
                for (BlockPos pos : packet.positions()) {
                    displayProviderInfo(player, level, pos, index++);
                }
                
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.error("Error handling pattern provider response", e);
            }
            AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Client response processed ===");
        });
    }
    
    @SuppressWarnings("null")
    private static void displayMessage(net.minecraft.world.entity.player.Player player, String key, Object arg, boolean actionBar) {
        try {
            Component message = arg != null ? Component.translatable(key, arg) : Component.translatable(key);
            player.displayClientMessage(message, actionBar);
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error displaying message", e);
        }
    }
    
    @SuppressWarnings("null")
    private static void displayProviderInfo(net.minecraft.world.entity.player.Player player, Level level, BlockPos pos, int index) {
        try {
            String dimensionStr = level.dimension().location().toString();
            
            double distance = Math.sqrt(player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            
            MutableComponent baseMessage = Component.literal("Provider " + index + ": ");
            
            Component dimComponent = Component.literal(dimensionStr)
                    .withStyle(Style.EMPTY
                            .withColor(TextColor.fromRgb(0x00FFFF))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                                    "/execute in " + dimensionStr + " run tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to teleport to this dimension"))));
            
            Component posComponent = Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                    .withStyle(Style.EMPTY
                            .withColor(TextColor.fromRgb(0x00FF00))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                                    "/tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to teleport to this position"))));
            
            Component distanceComponent = Component.literal(String.format("%.1f blocks", distance))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFF00)));
            
            MutableComponent message = baseMessage
                    .append(dimComponent)
                    .append(Component.literal(" at "))
                    .append(posComponent)
                    .append(Component.literal(" ("))
                    .append(distanceComponent)
                    .append(Component.literal(")"));
            
            player.displayClientMessage(message, false);
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error creating provider message", e);
        }
    }
}
