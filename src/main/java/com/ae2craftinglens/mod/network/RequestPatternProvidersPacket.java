package com.ae2craftinglens.mod.network;

import java.lang.reflect.Method;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestPatternProvidersPacket(Object what) implements CustomPacketPayload {
    
    public static final Type<RequestPatternProvidersPacket> TYPE = 
            new Type<>(ResourceLocation.fromNamespaceAndPath(AE2CraftingLens.MODID, "request_pattern_providers"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPatternProvidersPacket> STREAM_CODEC = 
            StreamCodec.of(
                    RequestPatternProvidersPacket::encode,
                    RequestPatternProvidersPacket::decode
            );
    
    private static void encode(RegistryFriendlyByteBuf buffer, RequestPatternProvidersPacket packet) {
        try {
            if (packet.what == null) {
                buffer.writeBoolean(false);
                return;
            }
            buffer.writeBoolean(true);
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Method writeKeyMethod = aeKeyClass.getMethod("writeKey", RegistryFriendlyByteBuf.class, aeKeyClass);
            writeKeyMethod.invoke(null, buffer, packet.what);
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error encoding AEKey", e);
            buffer.writeBoolean(false);
        }
    }
    
    private static RequestPatternProvidersPacket decode(RegistryFriendlyByteBuf buffer) {
        try {
            boolean hasKey = buffer.readBoolean();
            if (!hasKey) {
                return new RequestPatternProvidersPacket(null);
            }
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Method readKeyMethod = aeKeyClass.getMethod("readKey", RegistryFriendlyByteBuf.class);
            Object what = readKeyMethod.invoke(null, buffer);
            return new RequestPatternProvidersPacket(what);
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error decoding AEKey", e);
            return new RequestPatternProvidersPacket(null);
        }
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(RequestPatternProvidersPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> PatternProviderRequestHandler.handle(packet, context));
    }
}
