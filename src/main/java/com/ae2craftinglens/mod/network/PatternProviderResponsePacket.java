package com.ae2craftinglens.mod.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PatternProviderResponsePacket(Map<ResourceKey<Level>, Set<BlockPos>> positions) implements CustomPacketPayload {
    
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
        packet.positions().forEach((dimension, positions) -> {
            buffer.writeResourceKey(dimension);
            buffer.writeInt(positions.size());
            for (BlockPos pos : positions) {
                buffer.writeBlockPos(pos);
            }
        });
    }
    
    private static PatternProviderResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        int dimCount = buffer.readInt();
        Map<ResourceKey<Level>, Set<BlockPos>> result = new HashMap<>();
        
        for (int i = 0; i < dimCount; i++) {
            ResourceKey<Level> dimension = buffer.readResourceKey(Registries.DIMENSION);
            int posCount = buffer.readInt();
            Set<BlockPos> positions = new HashSet<>();
            for (int j = 0; j < posCount; j++) {
                positions.add(buffer.readBlockPos());
            }
            result.put(dimension, positions);
        }
        return new PatternProviderResponsePacket(result);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(PatternProviderResponsePacket packet, IPayloadContext context) {
        if (FMLLoader.getDist() == Dist.CLIENT) {
            try {
                Class<?> handlerClass = Class.forName("com.ae2craftinglens.mod.network.ClientPacketHandler");
                java.lang.reflect.Method handleMethod = handlerClass.getMethod("handle", PatternProviderResponsePacket.class, IPayloadContext.class);
                handleMethod.invoke(null, packet, context);
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.error("Error invoking client packet handler", e);
            }
        }
    }
}
