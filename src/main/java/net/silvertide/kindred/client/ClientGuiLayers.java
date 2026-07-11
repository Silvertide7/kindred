package net.silvertide.kindred.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.client.screen.HoldActionOverlay;

@Mod.EventBusSubscriber(modid = Kindred.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientGuiLayers {

    @SubscribeEvent
    public static void onRegister(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("hold_action", HoldActionOverlay::render);
    }

    private ClientGuiLayers() {}
}
