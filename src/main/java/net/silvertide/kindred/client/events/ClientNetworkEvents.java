package net.silvertide.kindred.client.events;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.client.data.ClientRosterData;
import net.silvertide.kindred.client.data.HoldActionState;
import net.silvertide.kindred.client.data.PreviewEntityCache;

/**
 * Client-side lifecycle cleanup. The Minecraft client JVM survives disconnects,
 * so any static state we keep needs an explicit clear on logout — otherwise a
 * reconnect (or loading a different world in single-player) briefly sees stale
 * data from the previous session until the new server's first sync arrives.
 *
 * <p>Mirror of the server-side cleanup in {@code PlayerEvents.onServerStopping}:
 * same shape (clear singletons whose static fields outlive the connection),
 * different lifecycle hook (logout vs server-stop).</p>
 */
@EventBusSubscriber(modid = Kindred.MODID, value = Dist.CLIENT)
public final class ClientNetworkEvents {

    /**
     * Fires when the local player disconnects from any server — single-player
     * exit to title, multiplayer disconnect, kick, or any connection drop.
     * Clears every piece of client-side static state we own.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRosterData.clear();
        HoldActionState.applyServerStop();
        // Defensive — also cleared on every S2CRosterSync, but if we disconnect
        // before any sync arrives in the next session we don't want yesterday's
        // preview entities lurking in the cache.
        PreviewEntityCache.clear();
    }

    private ClientNetworkEvents() {}
}
