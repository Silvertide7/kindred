package net.silvertide.kindred;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.silvertide.kindred.config.ClientConfig;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.network.Networking;
import org.slf4j.Logger;

@Mod(Kindred.MODID)
public class Kindred {
    public static final String MODID = "kindred";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Kindred() {
        Networking.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}
