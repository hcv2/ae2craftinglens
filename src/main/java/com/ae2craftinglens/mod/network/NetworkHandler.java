package com.ae2craftinglens.mod.network;

import com.ae2craftinglens.mod.AE2CraftingLens;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::registerPayloads);
    }
    
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AE2CraftingLens.MODID);
        
        @SuppressWarnings("null")
        var requestType = RequestPatternProvidersPacket.TYPE;
        @SuppressWarnings("null")
        var requestCodec = RequestPatternProvidersPacket.STREAM_CODEC;
        registrar.playToServer(
                requestType,
                requestCodec,
                RequestPatternProvidersPacket::handle
        );
        
        @SuppressWarnings("null")
        var responseType = PatternProviderResponsePacket.TYPE;
        @SuppressWarnings("null")
        var responseCodec = PatternProviderResponsePacket.STREAM_CODEC;
        registrar.playToClient(
                responseType,
                responseCodec,
                PatternProviderResponsePacket::handle
        );
    }
}
