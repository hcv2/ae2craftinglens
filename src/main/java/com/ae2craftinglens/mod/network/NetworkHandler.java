package com.ae2craftinglens.mod.network;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    
    private static final String PROTOCOL_VERSION = "1";
    
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AE2CraftingLens.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    
    private static int messageIndex = 0;
    
    public static void register() {
        CHANNEL.registerMessage(
                messageIndex++,
                RequestPatternProvidersPacket.class,
                RequestPatternProvidersPacket::encode,
                RequestPatternProvidersPacket::decode,
                RequestPatternProvidersPacket::handle
        );
        
        CHANNEL.registerMessage(
                messageIndex++,
                PatternProviderResponsePacket.class,
                PatternProviderResponsePacket::encode,
                PatternProviderResponsePacket::decode,
                PatternProviderResponsePacket::handle
        );
        
        AE2CraftingLens.LOGGER.info("Network channels registered");
    }
    
    public static void sendToServer(Object msg) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), msg);
    }
}
