package com.ae2craftinglens.mod;

import org.slf4j.Logger;
import com.ae2craftinglens.mod.network.NetworkHandler;
import com.mojang.logging.LogUtils;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(AE2CraftingLens.MODID)
public class AE2CraftingLens {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "ae2craftinglens";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    
    public static boolean isDebugLoggingEnabled() {
        return Config.ENABLE_DEBUG_LOGS.get();
    }

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    public AE2CraftingLens() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::clientSetup);

        NetworkHandler.register();

        FMLJavaModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        AE2CraftingLens.LOGGER.info("AE2CraftingLens mod initialized");
    }
    
    private void clientSetup(FMLClientSetupEvent event) {
        // Register client-side event handlers
        AE2CraftingLens.LOGGER.info("Client setup started");
        
        // Register our crafting screen handler directly
        MinecraftForge.EVENT_BUS.register(new CraftingScreenHandler());
        AE2CraftingLens.LOGGER.info("CraftingScreenHandler registered");
        
        // Register highlight renderer
        MinecraftForge.EVENT_BUS.register(PatternProviderHighlightRenderer.class);
        AE2CraftingLens.LOGGER.info("PatternProviderHighlightRenderer registered");
        
        AE2CraftingLens.LOGGER.info("Client setup completed");
    }
}
