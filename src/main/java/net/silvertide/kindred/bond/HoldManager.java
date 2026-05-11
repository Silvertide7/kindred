package net.silvertide.kindred.bond;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.silvertide.kindred.bond.bond_results.SummonResult;
import net.silvertide.kindred.network.ServerPacketHandler;
import net.silvertide.kindred.network.packet.S2CHoldStart;
import net.silvertide.kindred.network.packet.S2CHoldStop;
import net.silvertide.kindred.registry.ModAttachments;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HoldManager {
    private static final HoldManager INSTANCE = new HoldManager();
    private HoldManager() {}

    public static HoldManager get() {
        return INSTANCE;
    }

    public enum Action { SUMMON_KEYBIND, SUMMON_BOND, DISMISS, BREAK }

    public record ActiveHold(Action action, UUID bondId, long startTick, long endTick) {}

    private final Map<UUID, ActiveHold> activeHoldsByPlayer = new ConcurrentHashMap<>();

    public boolean isHolding(UUID playerId) {
        return activeHoldsByPlayer.containsKey(playerId);
    }

    public void requestStart(ServerPlayer player, Action action, UUID bondId) {
        HoldEligibility.Result eligibility = HoldEligibility.check(player, action, bondId);
        if (eligibility instanceof HoldEligibility.Result.Denied denied) {
            player.displayClientMessage(Component.translatable(denied.translationKey()), true);
            return;
        }
        long durationTicks = ((HoldEligibility.Result.Allowed) eligibility).durationTicks();
        long startTick = player.serverLevel().getGameTime();
        long endTick = startTick + durationTicks;

        activeHoldsByPlayer.put(player.getUUID(), new ActiveHold(action, bondId, startTick, endTick));

        Optional<UUID> bondIdForPacket = bondId == null ? Optional.empty() : Optional.of(bondId);
        PacketDistributor.sendToPlayer(player, new S2CHoldStart(action, bondIdForPacket, startTick, endTick));
    }

    public void cancel(ServerPlayer player) {
        if (activeHoldsByPlayer.remove(player.getUUID()) == null) return;
        PacketDistributor.sendToPlayer(player, new S2CHoldStop());
    }

    public void tickAll(MinecraftServer server) {
        if (activeHoldsByPlayer.isEmpty()) return;
        Iterator<Map.Entry<UUID, ActiveHold>> iterator = activeHoldsByPlayer.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveHold> entry = iterator.next();
            ActiveHold hold = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (player.serverLevel().getGameTime() < hold.endTick()) continue;

            iterator.remove();
            executeAction(player, hold);
            PacketDistributor.sendToPlayer(player, new S2CHoldStop());
        }
    }

    public void clear() {
        activeHoldsByPlayer.clear();
    }

    private void executeAction(ServerPlayer player, ActiveHold hold) {
        switch (hold.action()) {
            case SUMMON_KEYBIND -> {
                Optional<UUID> activePetId = player.getData(ModAttachments.BOND_ROSTER.get()).activePetId();
                if (activePetId.isEmpty()) {
                    player.sendSystemMessage(Component.translatable("kindred.summon.no_active"));
                    return;
                }
                executeSummon(player, activePetId.get());
            }
            case SUMMON_BOND -> executeSummon(player, hold.bondId());
            case DISMISS -> {
                BondService.dismiss(player, hold.bondId());
                ServerPacketHandler.sendRosterSync(player);
            }
            case BREAK -> {
                BondService.breakBond(player, hold.bondId());
                ServerPacketHandler.sendRosterSync(player);
            }
        }
    }

    private void executeSummon(ServerPlayer player, UUID bondId) {
        SummonResult result = BondService.summon(player, bondId);
        if (result != SummonResult.NO_SUCH_BOND) {
            result.translationKey()
                    .map(Component::translatable)
                    .ifPresent(player::sendSystemMessage);
        }
        if (result.isSuccess()) {
            ServerPacketHandler.sendRosterSync(player);
        }
    }
}
