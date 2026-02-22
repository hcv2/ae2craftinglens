package com.ae2craftinglens.mod.network;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::registerPayloads);
    }
    
    @SuppressWarnings("null")
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AE2CraftingLens.MODID);
        
        registrar.playToServer(
                RequestPatternProvidersPacket.TYPE,
                RequestPatternProvidersPacket.STREAM_CODEC,
                RequestPatternProvidersPacket::handle
        );
        
        registrar.playToClient(
                PatternProviderResponsePacket.TYPE,
                PatternProviderResponsePacket.STREAM_CODEC,
                PatternProviderResponsePacket::handle
        );
    }
}
