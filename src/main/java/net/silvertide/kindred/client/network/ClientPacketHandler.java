package net.silvertide.kindred.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.silvertide.kindred.client.data.ClientRosterData;
import net.silvertide.kindred.client.data.HoldActionState;
import net.silvertide.kindred.client.data.PreviewEntityCache;
import net.silvertide.kindred.client.screen.RosterScreen;
import net.silvertide.kindred.network.packet.S2CBindCandidateResult;
import net.silvertide.kindred.network.packet.S2CHoldStart;
import net.silvertide.kindred.network.packet.S2CHoldStop;
import net.silvertide.kindred.network.packet.S2CRosterSync;

public final class ClientPacketHandler {
    public static void onRosterSync(S2CRosterSync payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientRosterData.update(payload.bonds(), payload.globalCooldownRemainingMs(), payload.effectiveMaxBonds());
            PreviewEntityCache.clear();
        });
    }

    public static void onBindCandidateResult(S2CBindCandidateResult payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Screen current = Minecraft.getInstance().screen;
            if (current instanceof RosterScreen rs) {
                rs.onBindCandidateResult(payload.entityUUID(), payload.canBind(), payload.denyMessageKey());
            }
        });
    }

    public static void onHoldStart(S2CHoldStart payload, IPayloadContext context) {
        context.enqueueWork(() ->
                HoldActionState.applyServerStart(payload.action(), payload.bondId(),
                        payload.startTick(), payload.endTick()));
    }

    public static void onHoldStop(S2CHoldStop payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            HoldActionState.applyServerStop();
            Screen openScreen = Minecraft.getInstance().screen;
            if (openScreen instanceof RosterScreen rosterScreen) {
                rosterScreen.cancelRowHold();
            }
        });
    }

    private ClientPacketHandler() {}
}
