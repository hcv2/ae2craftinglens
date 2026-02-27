package com.ae2craftinglens.mod.network;

import java.util.Map;
import java.util.Set;

import com.ae2craftinglens.mod.AE2CraftingLens;
import com.ae2craftinglens.mod.PatternProviderHighlightManager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientPacketHandler {
    
    public static void handle(PatternProviderResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Client received pattern provider response ===");
        
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            try {
                AE2CraftingLens.LOGGER.info("Processing response packet with {} dimensions", packet.positions.size());
                
                Minecraft mc = Minecraft.getInstance();
                Player player = mc.player;
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
                
                if (packet.positions.isEmpty()) {
                    displayMessage(player, "message.ae2craftinglens.no_providers_found", null, false);
                    return;
                }
                
                int totalCount = packet.positions.values().stream().mapToInt(Set::size).sum();
                displayMessage(player, "message.ae2craftinglens.highlighted_providers", totalCount, true);
                
                int index = 1;
                for (Map.Entry<ResourceKey<Level>, Set<BlockPos>> entry : packet.positions.entrySet()) {
                    ResourceKey<Level> dimension = entry.getKey();
                    for (BlockPos pos : entry.getValue()) {
                        manager.addHighlightedProvider(player.getUUID(), dimension, pos);
                        displayProviderInfo(player, dimension, pos, index++);
                    }
                }
                
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.error("Error handling pattern provider response", e);
            }
            AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Client response processed ===");
        });
        
        context.setPacketHandled(true);
    }
    
    @SuppressWarnings("null")
    private static void displayMessage(Player player, String key, Object arg, boolean actionBar) {
        try {
            Component message = arg != null ? Component.translatable(key, arg) : Component.translatable(key);
            player.displayClientMessage(message, actionBar);
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error displaying message", e);
        }
    }
    
    @SuppressWarnings("null")
    private static void displayProviderInfo(Player player, ResourceKey<Level> dimension, BlockPos pos, int index) {
        try {
            String dimensionStr = dimension.location().toString();
            
            Component distanceComponent;
            if (player.level().dimension().equals(dimension)) {
                double distance = Math.sqrt(player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                distanceComponent = Component.literal(String.format("%.1f blocks", distance))
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFF00)));
            } else {
                distanceComponent = Component.literal("Different Dimension")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF0000)));
            }
            
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
                                    "/execute in " + dimensionStr + " run tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to teleport to this position"))));
            
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
