package net.silvertide.kindred.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.client.input.Keybinds;

@Mod.EventBusSubscriber(modid = Kindred.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(Keybinds.SUMMON_ACTIVE_PET);
        event.register(Keybinds.OPEN_ROSTER);
    }

    private ClientSetup() {}
}
