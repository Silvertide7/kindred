package net.silvertide.kindred.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.client.screen.HoldActionOverlay;

@EventBusSubscriber(modid = Kindred.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientGuiLayers {

    @SubscribeEvent
    public static void onRegister(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "hold_action"),
                HoldActionOverlay::render
        );
    }

    private ClientGuiLayers() {}
}
