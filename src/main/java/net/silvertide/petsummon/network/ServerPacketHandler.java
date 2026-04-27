package net.silvertide.petsummon.network;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.silvertide.petsummon.attachment.Bond;
import net.silvertide.petsummon.attachment.BondRoster;
import net.silvertide.petsummon.network.packet.C2SBreakBond;
import net.silvertide.petsummon.network.packet.C2SOpenRoster;
import net.silvertide.petsummon.network.packet.C2SSummonBond;
import net.silvertide.petsummon.network.packet.C2SSummonByKeybind;
import net.silvertide.petsummon.network.packet.S2CRosterSync;
import net.silvertide.petsummon.registry.ModAttachments;
import net.silvertide.petsummon.server.BondManager;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ServerPacketHandler {

    public static void onOpenRoster(C2SOpenRoster payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            sendRosterSync(player);
        });
    }

    public static void onSummonByKeybind(C2SSummonByKeybind payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
            if (roster.bonds().isEmpty()) {
                player.sendSystemMessage(Component.literal("No bonds to summon."));
                return;
            }
            // Pick most-recently-summoned. Ties (e.g. all 0) resolve to whichever
            // the stream picks; acceptable for a "summon last used" hotkey.
            Optional<Bond> pick = roster.bonds().values().stream()
                    .max(Comparator.comparingLong(Bond::lastSummonedAt));
            if (pick.isEmpty()) return;
            BondManager.SummonResult result = BondManager.summon(player, pick.get().bondId());
            player.sendSystemMessage(Component.literal("Summon: " + result.name()));
            if (isSummonSuccess(result)) sendRosterSync(player);
        });
    }

    public static void onSummonBond(C2SSummonBond payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BondManager.SummonResult result = BondManager.summon(player, payload.bondId());
            player.sendSystemMessage(Component.literal("Summon: " + result.name()));
            if (isSummonSuccess(result)) sendRosterSync(player);
        });
    }

    public static void onBreakBond(C2SBreakBond payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BondManager.BreakResult result = BondManager.breakBond(player, payload.bondId());
            player.sendSystemMessage(Component.literal("Break: " + result.name()));
            sendRosterSync(player);
        });
    }

    public static void sendRosterSync(ServerPlayer player) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        List<BondView> views = roster.bonds().values().stream()
                .sorted(Comparator.comparingLong(Bond::bondedAt))
                .map(BondView::from)
                .toList();
        PacketDistributor.sendToPlayer(player, new S2CRosterSync(views));
    }

    private static boolean isSummonSuccess(BondManager.SummonResult result) {
        return result == BondManager.SummonResult.WALKING
                || result == BondManager.SummonResult.TELEPORTED_NEAR
                || result == BondManager.SummonResult.SUMMONED_FRESH;
    }

    private ServerPacketHandler() {}
}
