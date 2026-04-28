package net.silvertide.kindred;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.registry.ModAttachments;
import org.slf4j.Logger;

@Mod(Kindred.MODID)
public class Kindred {
    public static final String MODID = "kindred";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Kindred(IEventBus modBus, ModContainer container) {
        ModAttachments.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}
