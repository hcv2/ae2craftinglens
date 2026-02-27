package com.ae2craftinglens.mod.network;

import java.lang.reflect.Method;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class RequestPatternProvidersPacket {
    
    private final Object what;
    private final int rowIndex;
    
    public RequestPatternProvidersPacket(Object what, int rowIndex) {
        this.what = what;
        this.rowIndex = rowIndex;
    }
    
    public Object getWhat() {
        return what;
    }
    
    public int getRowIndex() {
        return rowIndex;
    }
    
    public static void encode(RequestPatternProvidersPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.rowIndex);
        try {
            if (packet.what == null) {
                buffer.writeBoolean(false);
                return;
            }
            buffer.writeBoolean(true);
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Method writeKeyMethod = aeKeyClass.getMethod("write", FriendlyByteBuf.class, aeKeyClass);
            writeKeyMethod.invoke(null, buffer, packet.what);
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error encoding AEKey", e);
            buffer.writeBoolean(false);
        }
    }
    
    public static RequestPatternProvidersPacket decode(FriendlyByteBuf buffer) {
        int rowIndex = buffer.readInt();
        try {
            boolean hasKey = buffer.readBoolean();
            if (!hasKey) {
                return new RequestPatternProvidersPacket(null, rowIndex);
            }
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Method readKeyMethod = aeKeyClass.getMethod("read", FriendlyByteBuf.class);
            Object what = readKeyMethod.invoke(null, buffer);
            return new RequestPatternProvidersPacket(what, rowIndex);
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Error decoding AEKey", e);
            return new RequestPatternProvidersPacket(null, rowIndex);
        }
    }
    
    public static void handle(RequestPatternProvidersPacket packet, java.util.function.Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> PatternProviderRequestHandler.handle(packet, context));
        context.setPacketHandled(true);
    }
}
