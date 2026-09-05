package com.sfm_optimizer;

import com.sfm_optimizer.config.SFMOptimizerConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SFMOptimizer.MOD_ID)
public final class SFMOptimizer {
    public static final String MOD_ID = "sfm_optimizer";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public SFMOptimizer() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SFMOptimizerConfig.SPEC);
    }
}
