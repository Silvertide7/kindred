package net.silvertide.petsummon;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.silvertide.petsummon.config.Config;
import net.silvertide.petsummon.registry.ModAttachments;
import org.slf4j.Logger;

@Mod(PetSummon.MODID)
public class PetSummon {
    public static final String MODID = "petsummon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PetSummon(IEventBus modBus, ModContainer container) {
        ModAttachments.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}
