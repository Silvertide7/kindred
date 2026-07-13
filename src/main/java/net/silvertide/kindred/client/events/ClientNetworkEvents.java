package net.silvertide.kindred.client.events;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.client.data.ClientRosterData;
import net.silvertide.kindred.client.data.HoldActionState;
import net.silvertide.kindred.client.data.PreviewEntityCache;

@EventBusSubscriber(modid = Kindred.MODID, value = Dist.CLIENT)
public final class ClientNetworkEvents {

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRosterData.clear();
        HoldActionState.applyServerStop();
        PreviewEntityCache.clear();
    }

    private ClientNetworkEvents() {}
}
