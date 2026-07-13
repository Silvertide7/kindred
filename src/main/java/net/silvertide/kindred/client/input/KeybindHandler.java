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
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.network.BondView;
import net.silvertide.kindred.network.packet.C2SCancelHold;
import net.silvertide.kindred.network.packet.C2SRequestHold;

import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = Kindred.MODID, value = Dist.CLIENT)
public final class KeybindHandler {

    private static boolean wasKeyDownLastTick = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (Keybinds.OPEN_ROSTER.consumeClick()) {
            Minecraft.getInstance().setScreen(new RosterScreen());
        }

        if (Minecraft.getInstance().player == null) return;

        boolean isKeyDownNow = Keybinds.SUMMON_ACTIVE_PET.isDown();

        if (!wasKeyDownLastTick && isKeyDownNow) {
            wasKeyDownLastTick = true;
            sendRequestForCurrentIntent();
        } else if (wasKeyDownLastTick && !isKeyDownNow) {
            wasKeyDownLastTick = false;
            PacketDistributor.sendToServer(new C2SCancelHold());
        }
    }

    private static void sendRequestForCurrentIntent() {
        BondView nearbyActivePet = Config.ALLOW_DISMISSING.get() ? findNearbyActivePet() : null;
        if (nearbyActivePet != null) {
            PacketDistributor.sendToServer(new C2SRequestHold(
                    HoldManager.Action.DISMISS, Optional.of(nearbyActivePet.bondId())));
        } else {
            PacketDistributor.sendToServer(new C2SRequestHold(
                    HoldManager.Action.SUMMON_KEYBIND, Optional.empty()));
        }
    }

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
