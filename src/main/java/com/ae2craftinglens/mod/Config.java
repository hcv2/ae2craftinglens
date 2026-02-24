package com.ae2craftinglens.mod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGS = BUILDER
            .comment("Enable debug logging output for troubleshooting")
            .define("enableDebugLogs", false);

    static final ModConfigSpec SPEC = BUILDER.build();
}
