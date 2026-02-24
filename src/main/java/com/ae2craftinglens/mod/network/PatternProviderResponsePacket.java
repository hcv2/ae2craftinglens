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
        AE2CraftingLens.LOGGER.info("=== AE2 Crafting Lens: Client received pattern provider response ===");
        
        if (!context.flow().isClientbound()) {
            AE2CraftingLens.LOGGER.info("Not a clientbound packet, skipping");
            return;
        }
        
        context.enqueueWork(() -> {
            try {
                AE2CraftingLens.LOGGER.info("Processing response packet with {} positions", packet.positions().size());
                
                Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
                Method getInstanceMethod = minecraftClass.getMethod("getInstance");
                Object minecraft = getInstanceMethod.invoke(null);
                
                var playerField = minecraftClass.getDeclaredField("player");
                playerField.setAccessible(true);
                Object player = playerField.get(minecraft);
                if (player == null) {
                    AE2CraftingLens.LOGGER.warn("Player is null, skipping");
                    return;
                }
                
                var levelField = minecraftClass.getDeclaredField("level");
                levelField.setAccessible(true);
                Object level = levelField.get(minecraft);
                if (level == null) {
                    AE2CraftingLens.LOGGER.warn("Level is null, skipping");
                    return;
                }
                
                PatternProviderHighlightManager manager = PatternProviderHighlightManager.getInstance();
                
                if (packet.positions().isEmpty()) {
                    displayMessage(player, "message.ae2craftinglens.no_providers_found", false);
                    return;
                }
                
                java.util.UUID playerId = (java.util.UUID) player.getClass().getMethod("getUUID").invoke(player);
                
                for (BlockPos pos : packet.positions()) {
                    manager.addHighlightedProvider(playerId, (net.minecraft.world.level.Level) level, pos);
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
    
    private static void displayMessage(Object player, String key, Object... args) {
        try {
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Method translatableMethod = componentClass.getMethod("translatable", String.class, Object[].class);
            Object message = translatableMethod.invoke(null, key, args);
            Method displayClientMessageMethod = player.getClass().getMethod("displayClientMessage", componentClass, boolean.class);
            displayClientMessageMethod.invoke(player, message, true);
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error displaying message", e);
        }
    }
    
    private static void displayProviderInfo(Object player, Object level, BlockPos pos, int index) {
        try {
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> styleClass = Class.forName("net.minecraft.network.chat.Style");
            Class<?> textColorClass = Class.forName("net.minecraft.network.chat.TextColor");
            Class<?> clickEventClass = Class.forName("net.minecraft.network.chat.ClickEvent");
            Class<?> clickEventActionClass = Class.forName("net.minecraft.network.chat.ClickEvent$Action");
            Class<?> hoverEventClass = Class.forName("net.minecraft.network.chat.HoverEvent");
            
            Method literalMethod = componentClass.getMethod("literal", String.class);
            Method fromRgbMethod = textColorClass.getMethod("fromRgb", int.class);
            Method withColorMethod = styleClass.getMethod("withColor", textColorClass);
            Method withClickEventMethod = styleClass.getMethod("withClickEvent", clickEventClass);
            Method withHoverEventMethod = styleClass.getMethod("withHoverEvent", hoverEventClass);
            Method withStyleMethod = componentClass.getMethod("withStyle", styleClass);
            Method appendMethod = componentClass.getMethod("append", componentClass);
            
            Object emptyStyle = styleClass.getField("EMPTY").get(null);
            Object runCommandAction = clickEventActionClass.getField("RUN_COMMAND").get(null);
            
            var dimensionMethod = level.getClass().getMethod("dimension");
            Object dimension = dimensionMethod.invoke(level);
            String dimensionStr = dimension.getClass().getMethod("location").invoke(dimension).toString();
            
            double distanceSqr = (double) player.getClass().getMethod("distanceToSqr", double.class, double.class, double.class)
                    .invoke(player, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double distance = Math.sqrt(distanceSqr);
            
            Object baseMessage = literalMethod.invoke(null, "Provider " + index + ": ");
            
            Object dimComponent = literalMethod.invoke(null, dimensionStr);
            Object style = withColorMethod.invoke(emptyStyle, fromRgbMethod.invoke(null, 0x00FFFF));
            
            Object clickEvent = clickEventClass.getConstructor(clickEventActionClass, String.class)
                    .newInstance(runCommandAction, "/execute in " + dimensionStr + " run tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            style = withClickEventMethod.invoke(style, clickEvent);
            
            Object hoverEvent = createHoverEvent(hoverEventClass, componentClass, literalMethod.invoke(null, "Click to teleport to this dimension"));
            if (hoverEvent != null) {
                style = withHoverEventMethod.invoke(style, hoverEvent);
            }
            
            dimComponent = withStyleMethod.invoke(dimComponent, style);
            baseMessage = appendMethod.invoke(baseMessage, dimComponent);
            baseMessage = appendMethod.invoke(baseMessage, literalMethod.invoke(null, " at "));
            
            Object posComponent = literalMethod.invoke(null, pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            style = withColorMethod.invoke(emptyStyle, fromRgbMethod.invoke(null, 0x00FF00));
            clickEvent = clickEventClass.getConstructor(clickEventActionClass, String.class)
                    .newInstance(runCommandAction, "/tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            style = withClickEventMethod.invoke(style, clickEvent);
            
            hoverEvent = createHoverEvent(hoverEventClass, componentClass, literalMethod.invoke(null, "Click to teleport to this position"));
            if (hoverEvent != null) {
                style = withHoverEventMethod.invoke(style, hoverEvent);
            }
            
            posComponent = withStyleMethod.invoke(posComponent, style);
            baseMessage = appendMethod.invoke(baseMessage, posComponent);
            baseMessage = appendMethod.invoke(baseMessage, literalMethod.invoke(null, " ("));
            
            Object distanceComponent = literalMethod.invoke(null, String.format("%.1f blocks", distance));
            style = withColorMethod.invoke(emptyStyle, fromRgbMethod.invoke(null, 0xFFFF00));
            distanceComponent = withStyleMethod.invoke(distanceComponent, style);
            baseMessage = appendMethod.invoke(baseMessage, distanceComponent);
            baseMessage = appendMethod.invoke(baseMessage, literalMethod.invoke(null, ")"));
            
            Method displayClientMessageMethod = player.getClass().getMethod("displayClientMessage", componentClass, boolean.class);
            displayClientMessageMethod.invoke(player, baseMessage, false);
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error creating provider message", e);
        }
    }
    
    private static Object createHoverEvent(Class<?> hoverEventClass, Class<?> componentClass, Object text) {
        try {
            Class<?> hoverEventActionClass = Class.forName("net.minecraft.network.chat.HoverEvent$Action");
            Object showTextAction = hoverEventActionClass.getField("SHOW_TEXT").get(null);
            
            try {
                return hoverEventClass.getConstructor(hoverEventActionClass, componentClass)
                        .newInstance(showTextAction, text);
            } catch (Exception ignored) {}
            
            try {
                Method showTextMethod = hoverEventClass.getMethod("showText", componentClass);
                return showTextMethod.invoke(null, text);
            } catch (Exception ignored) {}
            
            try {
                Method ofMethod = hoverEventClass.getMethod("of", hoverEventActionClass, componentClass);
                return ofMethod.invoke(null, showTextAction, text);
            } catch (Exception ignored) {}
            
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.debug("Could not create HoverEvent: {}", e.getMessage());
        }
        return null;
    }
}
