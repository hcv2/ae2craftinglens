package com.ae2craftinglens.mod;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOGS = BUILDER
            .comment("Enable debug logging output for troubleshooting")
            .define("enableDebugLogs", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();
}
