package com.ae2craftinglens.mod;

import org.slf4j.Logger;

import com.ae2craftinglens.mod.network.NetworkHandler;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(AE2CraftingLens.MODID)
public class AE2CraftingLens {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "ae2craftinglens";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public AE2CraftingLens(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::clientSetup);

        NetworkHandler.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        AE2CraftingLens.LOGGER.info("AE2CraftingLens mod initialized");
    }
    
    private void clientSetup(FMLClientSetupEvent event) {
        // Register client-side event handlers
        AE2CraftingLens.LOGGER.info("Client setup started");
        
        // Register our crafting screen handler directly
        NeoForge.EVENT_BUS.register(new CraftingScreenHandler());
        AE2CraftingLens.LOGGER.info("CraftingScreenHandler registered");
        
        // Register highlight renderer
        NeoForge.EVENT_BUS.register(PatternProviderHighlightRenderer.class);
        AE2CraftingLens.LOGGER.info("PatternProviderHighlightRenderer registered");
        
        AE2CraftingLens.LOGGER.info("Client setup completed");
    }
}
