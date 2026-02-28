package com.ae2craftinglens.mod.network;

import java.lang.reflect.Method;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class RequestPatternProvidersPacket {
    
    // 缓存反射 Method 对象以提高性能
    private static Method WRITE_METHOD;
    private static Method READ_METHOD;
    
    static {
        try {
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            WRITE_METHOD = aeKeyClass.getMethod("writeToPacket", FriendlyByteBuf.class);
            READ_METHOD = aeKeyClass.getMethod("readFromPacket", FriendlyByteBuf.class);
            AE2CraftingLens.LOGGER.info("AEKey reflection methods initialized successfully");
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Failed to initialize AEKey reflection methods", e);
            WRITE_METHOD = null;
            READ_METHOD = null;
        }
    }
    
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
        // 严格按照顺序编码：rowIndex -> hasKey -> AEKey data
        buffer.writeInt(packet.rowIndex);
        boolean hasKey = (packet.what != null);
        buffer.writeBoolean(hasKey);
        
        if (hasKey) {
            try {
                if (WRITE_METHOD == null) {
                    AE2CraftingLens.LOGGER.error("WRITE_METHOD not initialized, cannot encode AEKey");
                    return;
                }
                // 使用缓存的 Method 对象调用 AEKey 的 writeToPacket 实例方法
                WRITE_METHOD.invoke(packet.what, buffer);
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.error("Error encoding AEKey", e);
            }
        }
    }
    
    public static RequestPatternProvidersPacket decode(FriendlyByteBuf buffer) {
        // 严格按照编码顺序解码：rowIndex -> hasKey -> AEKey data
        int rowIndex = buffer.readInt();
        Object what = null;
        
        boolean hasKey = buffer.readBoolean();
        if (hasKey) {
            try {
                if (READ_METHOD == null) {
                    AE2CraftingLens.LOGGER.error("READ_METHOD not initialized, cannot decode AEKey");
                    return new RequestPatternProvidersPacket(null, rowIndex);
                }
                // 使用缓存的 Method 对象调用 AEKey.readFromPacket 静态方法
                what = READ_METHOD.invoke(null, buffer);
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.error("Error decoding AEKey", e);
            }
        }
        
        return new RequestPatternProvidersPacket(what, rowIndex);
    }
    
    public static void handle(RequestPatternProvidersPacket packet, java.util.function.Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> PatternProviderRequestHandler.handle(packet, context));
        context.setPacketHandled(true);
    }
}
