package com.ae2craftinglens.mod.network;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class RequestPatternProvidersPacket {
    
    // Cache reflection Method objects for performance
    private static Method WRITE_METHOD;
    private static Method READ_METHOD;
    private static boolean INITIALIZED = false;
    
    static {
        try {
            // Try to find the correct AEKey serialization method
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            
            // Print all AEKey methods for debugging
            AE2CraftingLens.LOGGER.info("AEKey class: {}", aeKeyClass.getName());
            AE2CraftingLens.LOGGER.info("AEKey static methods (containing 'Key'):");
            for (Method m : aeKeyClass.getDeclaredMethods()) {
                if (m.getName().toLowerCase().contains("key")) {
                    int modifiers = m.getModifiers();
                    if (java.lang.reflect.Modifier.isStatic(modifiers)) {
                        AE2CraftingLens.LOGGER.info("  - {} ({} params, {})", 
                            m.getName(), m.getParameterCount(), m.getReturnType().getSimpleName());
                    }
                }
            }
            
            // Try to find readKey and writeKey methods (AE2 1.20.1 official methods)
            try {
                // writeKey(FriendlyByteBuf, AEKey)
                Method[] methods = aeKeyClass.getMethods();
                for (Method m : methods) {
                    if (m.getName().equals("writeKey") && m.getParameterCount() == 2) {
                        Class<?>[] paramTypes = m.getParameterTypes();
                        if (paramTypes[0].getName().equals("net.minecraft.network.FriendlyByteBuf")) {
                            WRITE_METHOD = m;
                            AE2CraftingLens.LOGGER.info("Found official writeKey method");
                            break;
                        }
                    }
                }
                
                // readKey(FriendlyByteBuf)
                for (Method m : methods) {
                    if (m.getName().equals("readKey") && m.getParameterCount() == 1) {
                        Class<?>[] paramTypes = m.getParameterTypes();
                        if (paramTypes[0].getName().equals("net.minecraft.network.FriendlyByteBuf")) {
                            READ_METHOD = m;
                            AE2CraftingLens.LOGGER.info("Found official readKey method");
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.warn("Could not find readKey/writeKey methods, will use fallback");
            }
            
            if (WRITE_METHOD != null && READ_METHOD != null) {
                INITIALIZED = true;
                AE2CraftingLens.LOGGER.info("AEKey reflection methods initialized successfully (using readKey/writeKey)");
            } else {
                AE2CraftingLens.LOGGER.warn("readKey/writeKey not found, falling back to NBT serialization");
                INITIALIZED = true; // Still allow NBT fallback
            }
        } catch (Exception e) {
            AE2CraftingLens.LOGGER.error("Failed to initialize AEKey reflection methods", e);
            INITIALIZED = false;
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
        // Strict encoding order: rowIndex -> hasKey -> AEKey data
        buffer.writeInt(packet.rowIndex);
        boolean hasKey = (packet.what != null);
        buffer.writeBoolean(hasKey);
        
        if (hasKey) {
            try {
                if (!INITIALIZED) {
                    AE2CraftingLens.LOGGER.error("AEKey methods not initialized, cannot encode AEKey");
                    return;
                }
                
                // Debug: Print AEKey actual type
                AE2CraftingLens.LOGGER.info("AEKey actual class: {}", packet.what.getClass().getName());
                
                // Try to use official readKey/writeKey methods first
                if (WRITE_METHOD != null) {
                    // Official AE2 method: writeKey(FriendlyByteBuf, AEKey)
                    WRITE_METHOD.invoke(null, buffer, packet.what);
                    AE2CraftingLens.LOGGER.info("Encoded AEKey using official writeKey method");
                } else {
                    // Fallback: Use NBT-based serialization
                    Class<?> aeKeyClass = packet.what.getClass();
                    Method toTagMethod = null;
                    try {
                        toTagMethod = aeKeyClass.getMethod("toTag");
                    } catch (NoSuchMethodException e) {
                        toTagMethod = aeKeyClass.getSuperclass().getMethod("toTag");
                    }
                    
                    if (toTagMethod != null) {
                        Object tag = toTagMethod.invoke(packet.what);
                        buffer.writeNbt((net.minecraft.nbt.CompoundTag) tag);
                        AE2CraftingLens.LOGGER.info("Encoded AEKey as NBT (fallback): {}", tag);
                    } else {
                        AE2CraftingLens.LOGGER.error("Could not find toTag method for AEKey");
                    }
                }
            } catch (Exception e) {
                AE2CraftingLens.LOGGER.error("Error encoding AEKey", e);
            }
        }
    }
    
    public static RequestPatternProvidersPacket decode(FriendlyByteBuf buffer) {
        // Strict decoding order: rowIndex -> hasKey -> AEKey data
        int rowIndex = buffer.readInt();
        Object what = null;
        
        boolean hasKey = buffer.readBoolean();
        if (hasKey) {
            try {
                if (!INITIALIZED) {
                    AE2CraftingLens.LOGGER.error("AEKey methods not initialized, cannot decode AEKey");
                    return new RequestPatternProvidersPacket(null, rowIndex);
                }
                
                // Try to use official readKey method first
                if (READ_METHOD != null) {
                    // Official AE2 method: readKey(FriendlyByteBuf)
                    what = READ_METHOD.invoke(null, buffer);
                    AE2CraftingLens.LOGGER.info("Decoded AEKey using official readKey method: {}", what);
                } else {
                    // Fallback: Use NBT-based deserialization
                    net.minecraft.nbt.CompoundTag tag = buffer.readNbt();
                    AE2CraftingLens.LOGGER.info("Decoding AEKey from NBT (fallback): {}", tag);
                    
                    if (tag != null) {
                        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
                        Method fromTagMethod = aeKeyClass.getMethod("fromTag", net.minecraft.nbt.CompoundTag.class);
                        what = fromTagMethod.invoke(null, tag);
                        AE2CraftingLens.LOGGER.info("Decoded AEKey from NBT: {}", what);
                    }
                }
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
