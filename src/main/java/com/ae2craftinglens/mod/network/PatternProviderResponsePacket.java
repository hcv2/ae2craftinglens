package com.ae2craftinglens.mod.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

public class PatternProviderResponsePacket {
    
    public final Map<ResourceKey<Level>, Set<BlockPos>> positions;
    
    public PatternProviderResponsePacket(Map<ResourceKey<Level>, Set<BlockPos>> positions) {
        this.positions = positions;
    }
    
    public Map<ResourceKey<Level>, Set<BlockPos>> getPositions() {
        return positions;
    }
    
    public static void encode(PatternProviderResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.positions.size());
        packet.positions.forEach((dimension, positions) -> {
            buffer.writeResourceKey(dimension);
            buffer.writeInt(positions.size());
            for (BlockPos pos : positions) {
                buffer.writeBlockPos(pos);
            }
        });
    }
    
    public static PatternProviderResponsePacket decode(FriendlyByteBuf buffer) {
        int dimCount = buffer.readInt();
        Map<ResourceKey<Level>, Set<BlockPos>> result = new HashMap<>();
        
        for (int i = 0; i < dimCount; i++) {
            ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.resources.ResourceKey.createRegistryKey(new ResourceLocation("dimension_type")), buffer.readResourceLocation());
            int posCount = buffer.readInt();
            Set<BlockPos> positions = new HashSet<>();
            for (int j = 0; j < posCount; j++) {
                positions.add(buffer.readBlockPos());
            }
            result.put(dimension, positions);
        }
        return new PatternProviderResponsePacket(result);
    }
    
    public static void handle(PatternProviderResponsePacket packet, java.util.function.Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientPacketHandler.handle(packet, contextSupplier));
        context.setPacketHandled(true);
    }
}
