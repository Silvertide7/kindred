package net.silvertide.kindred.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.bond.HoldEligibility;
import net.silvertide.kindred.bond.HoldManager;
import net.silvertide.kindred.client.data.ClientRosterData;
import net.silvertide.kindred.client.screen.RosterScreen;
import net.silvertide.kindred.network.BondView;
import net.silvertide.kindred.network.packet.C2SCancelHold;
import net.silvertide.kindred.network.packet.C2SRequestHold;

import java.util.Optional;
import java.util.UUID;

/**
 * Client-side handling of the two Kindred keybinds:
 * <ul>
 *   <li>{@link Keybinds#OPEN_ROSTER} — a tap that opens the roster screen.</li>
 *   <li>{@link Keybinds#SUMMON_ACTIVE_PET} — a hold-to-confirm that fires either
 *       DISMISS (if the active pet is loaded and within
 *       {@link HoldEligibility#DISMISS_RADIUS} blocks) or SUMMON_KEYBIND
 *       (otherwise).</li>
 * </ul>
 *
 * <p>The summon/dismiss keybind uses edge detection on
 * {@link net.minecraft.client.KeyMapping#isDown()} between ticks (the
 * {@link #wasKeyDownLastTick} flag), rather than {@code consumeClick()}. This
 * mirrors Homebound's {@code ClientForgeEvents} pattern: the physical key state
 * is the single source of truth, with no second flag tracking "did we already
 * own a hold?" — so there's no opportunity for two flags to fall out of sync.</p>
 */
@EventBusSubscriber(modid = Kindred.MODID, value = Dist.CLIENT)
public final class KeybindHandler {

    /** Previous-tick value of {@code SUMMON_ACTIVE_PET.isDown()}. Comparing this
     *  to the current value each tick yields clean press/release edge detection.
     *  Single flag, no coordination with server state — the key IS the truth. */
    private static boolean wasKeyDownLastTick = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Open roster: tap. Distinct from the summon keybind because we want
        // exactly-one-action-per-press semantics here, which consumeClick gives us.
        while (Keybinds.OPEN_ROSTER.consumeClick()) {
            Minecraft.getInstance().setScreen(new RosterScreen());
        }

        if (Minecraft.getInstance().player == null) return;

        boolean isKeyDownNow = Keybinds.SUMMON_ACTIVE_PET.isDown();

        if (!wasKeyDownLastTick && isKeyDownNow) {
            // Press edge: ask the server to start a hold. Client picks the
            // intent locally (DISMISS if the active pet is nearby, else
            // SUMMON_KEYBIND) — the server re-validates distance and cooldowns
            // and either starts the hold or denies it via action-bar message.
            wasKeyDownLastTick = true;
            sendRequestForCurrentIntent();
        } else if (wasKeyDownLastTick && !isKeyDownNow) {
            // Release edge: tell the server to drop the hold. Idempotent on the
            // server (cancel for a non-existent hold is a silent no-op), so we
            // always send without checking whether the hold has been confirmed
            // — covers the race where press and release happen close enough
            // together that the start packet is still in flight.
            wasKeyDownLastTick = false;
            PacketDistributor.sendToServer(new C2SCancelHold());
        }
    }

    /**
     * Pick DISMISS or SUMMON_KEYBIND intent and send {@link C2SRequestHold}.
     * DISMISS is chosen when the active pet's loaded entity is within
     * {@link HoldEligibility#DISMISS_RADIUS} blocks of the local player —
     * otherwise the player wants to summon it back (whether it's far away in
     * the same dimension or unloaded entirely).
     */
    private static void sendRequestForCurrentIntent() {
        BondView nearbyActivePet = findNearbyActivePet();
        if (nearbyActivePet != null) {
            PacketDistributor.sendToServer(new C2SRequestHold(
                    HoldManager.Action.DISMISS, Optional.of(nearbyActivePet.bondId())));
        } else {
            PacketDistributor.sendToServer(new C2SRequestHold(
                    HoldManager.Action.SUMMON_KEYBIND, Optional.empty()));
        }
    }

    /**
     * Returns the active bond's view if its loaded entity exists in the local
     * level and is within {@link HoldEligibility#DISMISS_RADIUS} blocks of the
     * local player, otherwise null.
     *
     * <p>Strictly active-only: the keybind always targets the active pet. If the
     * active is nearby, holding dismisses it; if it's far or unloaded, holding
     * summons it. Other bonded pets in the area don't influence the keybind —
     * use the roster screen's Dismiss button to recall a non-active pet.</p>
     */
    private static BondView findNearbyActivePet() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        ClientLevel localLevel = minecraft.level;
        if (localPlayer == null || localLevel == null) return null;

        Optional<BondView> activeBond = ClientRosterData.findActive();
        if (activeBond.isEmpty()) return null;
        Entity activeEntity = findLoadedEntityByUuid(localLevel, activeBond.get().entityUUID());
        if (activeEntity == null) return null;
        if (activeEntity.distanceToSqr(localPlayer) > HoldEligibility.DISMISS_RADIUS_SQ) return null;
        return activeBond.get();
    }

    /**
     * Linear scan through entities currently being rendered for one matching
     * the given UUID. Cheap enough for the active-pet lookup that happens at
     * most once per keybind press.
     */
    private static Entity findLoadedEntityByUuid(ClientLevel level, UUID entityUuid) {
        for (Entity entity : level.entitiesForRendering()) {
            if (!entity.isRemoved() && entityUuid.equals(entity.getUUID())) return entity;
        }
        return null;
    }

    private KeybindHandler() {
        Kindred.LOGGER.trace("KeybindHandler init");
    }
}
