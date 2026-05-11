package net.silvertide.kindred.bond;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.silvertide.kindred.attachment.Bond;
import net.silvertide.kindred.attachment.BondRoster;
import net.silvertide.kindred.bond.bond_results.SummonResult;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.registry.ModAttachments;

import java.util.Optional;
import java.util.UUID;

public final class HoldEligibility {
    private HoldEligibility() {}

    public static final double DISMISS_RADIUS = 6.0D;
    public static final double DISMISS_RADIUS_SQ = DISMISS_RADIUS * DISMISS_RADIUS;

    private static final long BREAK_HOLD_TICKS = 20L;

    public sealed interface Result {
        record Allowed(long durationTicks) implements Result {}
        record Denied(String translationKey) implements Result {}
    }

    public static Result check(ServerPlayer player, HoldManager.Action action, UUID bondId) {
        return switch (action) {
            case SUMMON_KEYBIND -> checkSummonByKeybind(player);
            case SUMMON_BOND -> bondId == null
                    ? new Result.Denied("kindred.summon.no_such_bond")
                    : checkSummon(player, bondId, Config.holdToSummonTicks());
            case DISMISS -> bondId == null
                    ? new Result.Denied("kindred.dismiss.no_such_bond")
                    : checkDismiss(player, bondId);
            case BREAK -> bondId == null
                    ? new Result.Denied("kindred.break.no_such_bond")
                    : checkBreak(player, bondId);
        };
    }

    private static Result checkSummonByKeybind(ServerPlayer player) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        if (roster.bonds().isEmpty()) return new Result.Denied("kindred.summon.no_bonds");
        Optional<UUID> activePetId = roster.activePetId();
        if (activePetId.isEmpty()) return new Result.Denied("kindred.summon.no_active");
        return checkSummon(player, activePetId.get(), Config.holdToSummonTicks());
    }

    private static Result checkSummon(ServerPlayer player, UUID bondId, long durationTicks) {
        Optional<SummonResult> gateFailure = BondService.checkSummonGate(player, bondId);
        if (gateFailure.isPresent()) {
            return new Result.Denied(gateFailure.get().translationKey().orElseThrow());
        }
        return new Result.Allowed(durationTicks);
    }

    private static Result checkDismiss(ServerPlayer player, UUID bondId) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        Optional<Bond> maybeBond = roster.get(bondId);
        if (maybeBond.isEmpty()) return new Result.Denied("kindred.dismiss.no_such_bond");
        if (BondService.isRevivalPending(maybeBond.get())) {
            return new Result.Denied("kindred.dismiss.reviving");
        }

        Optional<Entity> loadedEntity = BondEntityIndex.get().find(bondId);
        if (loadedEntity.isEmpty()) return new Result.Denied("kindred.dismiss.not_loaded");

        return new Result.Allowed(Config.holdToDismissTicks());
    }

    private static Result checkBreak(ServerPlayer player, UUID bondId) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        Optional<Bond> maybeBond = roster.get(bondId);
        if (maybeBond.isEmpty()) return new Result.Denied("kindred.break.no_such_bond");
        if (BondService.isRevivalPending(maybeBond.get())) {
            return new Result.Denied("kindred.break.reviving");
        }
        return new Result.Allowed(BREAK_HOLD_TICKS);
    }
}
