package com.sfm_optimizer.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SFMOptimizerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_EVEN_SPLIT;
    public static final ModConfigSpec.IntValue SLEEP_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLE_SLOT_MEMORY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLE_EVEN_SPLIT = b.comment("Enable even-split for ROUND ROBIN output").define("enableEvenSplit", true);
        SLEEP_COOLDOWN_TICKS = b.comment("Ticks a full/empty slot sleeps before re-checking").defineInRange("sleepCooldownTicks", 20, 0, 2000);
        ENABLE_SLOT_MEMORY = b.comment("Remember last successful slot to reduce scanning").define("enableSlotMemory", true);
        SPEC = b.build();
    }
}
