package com.sfm_optimizer;

import com.sfm_optimizer.config.SFMOptimizerConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SFMOptimizer.MOD_ID)
public final class SFMOptimizer {
    public static final String MOD_ID = "sfm_optimizer";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public SFMOptimizer(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SFMOptimizerConfig.SPEC);
    }
}
