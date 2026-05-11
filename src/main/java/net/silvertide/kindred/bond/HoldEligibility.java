package net.silvertide.kindred.bond;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.silvertide.kindred.attachment.Bond;
import net.silvertide.kindred.attachment.BondRoster;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.registry.ModAttachments;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side gate for whether a player may begin a hold. Single source of truth —
 * the client never pre-checks. {@link HoldManager#requestStart} consults this and
 * either starts the hold (on {@link Result.Allowed}) or shows the player an
 * action-bar message (on {@link Result.Denied}).
 *
 * <p>Each {@link HoldManager.Action} has its own check method below; {@link #check}
 * dispatches by action. The {@code SUMMON_KEYBIND} path reuses the same per-bond
 * checks as {@code SUMMON_BOND} after resolving the active pet.</p>
 */
public final class HoldEligibility {
    private HoldEligibility() {}

    /** Maximum distance (in blocks) at which the keybind treats the active pet as
     *  "nearby enough to dismiss instead of summon." The client uses this as a
     *  local heuristic when deciding which intent to send; the server re-validates
     *  the actual distance in {@link #checkDismiss}, so a spoofed dismiss request
     *  from far away is rejected. */
    public static final double DISMISS_RADIUS = 6.0D;
    /** Pre-squared for cheap {@code distanceToSqr} comparisons. */
    public static final double DISMISS_RADIUS_SQ = DISMISS_RADIUS * DISMISS_RADIUS;

    /** Hardcoded by design: the X→Confirm UI flow IS the destructive-action gate,
     *  and exposing the duration as config just invites either accidental breaks
     *  (too short) or laggy-feeling UX (too long). 1 second = 20 ticks matches the
     *  original Kindred behavior before this refactor. */
    private static final long BREAK_HOLD_TICKS = 20L;

    /**
     * Result of an eligibility check. {@link Allowed} carries the hold's duration
     * in game ticks (consumed by {@link HoldManager#requestStart} to compute the
     * end tick); {@link Denied} carries a translation key the server uses to
     * surface a vanilla action-bar message.
     */
    public sealed interface Result {
        record Allowed(long durationTicks) implements Result {}
        record Denied(String translationKey) implements Result {}
    }

    /**
     * Dispatch entry point. Returns an {@link Result.Allowed} with the hold's
     * tick duration if the request is valid, or a {@link Result.Denied} with a
     * translation key for the action-bar message otherwise.
     *
     * <p>{@code bondId} must be present for every action except {@code SUMMON_KEYBIND}
     * (which resolves the active pet server-side); a null bondId for the other
     * actions is treated as a malformed packet and denied.</p>
     */
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

    // ------------ per-action checks ------------

    /**
     * Keybind summon: resolve the active pet pointer, then run the standard
     * per-bond summon checks against it. Failing here means either the player
     * has no bonds at all or has no active set — both are user-visible errors
     * worth distinct deny keys.
     */
    private static Result checkSummonByKeybind(ServerPlayer player) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        if (roster.bonds().isEmpty()) return new Result.Denied("kindred.summon.no_bonds");
        Optional<UUID> activePetId = roster.activePetId();
        if (activePetId.isEmpty()) return new Result.Denied("kindred.summon.no_active");
        return checkSummon(player, activePetId.get(), Config.holdToSummonTicks());
    }

    /**
     * Standard per-bond summon eligibility — cooldowns, revival state. Used by
     * both keybind and screen-button summon paths. Cooldowns are wall-clock based
     * (intentional — they should advance even while the game is paused, unlike
     * the hold duration itself which uses gameTime).
     */
    private static Result checkSummon(ServerPlayer player, UUID bondId, long durationTicks) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        Optional<Bond> maybeBond = roster.get(bondId);
        if (maybeBond.isEmpty()) return new Result.Denied("kindred.summon.no_such_bond");
        Bond bond = maybeBond.get();

        if (isRevivalPending(bond)) return new Result.Denied("kindred.summon.reviving");

        long nowMs = System.currentTimeMillis();
        long perBondCooldownMs = Config.SUMMON_COOLDOWN_TICKS.get() * 50L;
        if (nowMs - bond.lastSummonedAt() < perBondCooldownMs) {
            return new Result.Denied("kindred.summon.on_cooldown");
        }

        long globalCooldownMs = Config.summonGlobalCooldownMs();
        if (GlobalSummonCooldownTracker.get().remainingMs(player.getUUID(), globalCooldownMs) > 0L) {
            return new Result.Denied("kindred.summon.global_cooldown");
        }

        return new Result.Allowed(durationTicks);
    }

    /**
     * Dismiss eligibility — bond exists, not in post-death revival, and the entity
     * is loaded somewhere.
     *
     * <p>Intentionally NO distance or same-level check. Dismiss from the screen has
     * always worked from any distance and across dimensions (as long as the pet's
     * chunk was loaded somewhere). The keybind's "is the active pet nearby?"
     * decision happens client-side in {@code KeybindHandler.findNearbyActivePet} —
     * it only sends DISMISS when the pet is within {@link #DISMISS_RADIUS} blocks
     * in the local level; otherwise it sends SUMMON_KEYBIND. So legitimate
     * requests will never carry a far-away target, and a spoofed request can at
     * worst dismiss the player's own pet from afar — which the screen button
     * already allows.</p>
     */
    private static Result checkDismiss(ServerPlayer player, UUID bondId) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        Optional<Bond> maybeBond = roster.get(bondId);
        if (maybeBond.isEmpty()) return new Result.Denied("kindred.dismiss.no_such_bond");
        if (isRevivalPending(maybeBond.get())) return new Result.Denied("kindred.dismiss.reviving");

        Optional<Entity> loadedEntity = BondEntityIndex.get().find(bondId);
        if (loadedEntity.isEmpty()) return new Result.Denied("kindred.dismiss.not_loaded");

        return new Result.Allowed(Config.holdToDismissTicks());
    }

    /**
     * Break eligibility — the only requirement is that the bond still exists.
     * Duration is hardcoded (see {@link #BREAK_HOLD_TICKS}).
     */
    private static Result checkBreak(ServerPlayer player, UUID bondId) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        if (roster.get(bondId).isEmpty()) return new Result.Denied("kindred.break.no_such_bond");
        return new Result.Allowed(BREAK_HOLD_TICKS);
    }

    /**
     * True if the bond is in its post-death revival cooldown window. Returns
     * false when there's no death timestamp, or when revival cooldowns are
     * disabled by config (cooldownMs == 0).
     */
    private static boolean isRevivalPending(Bond bond) {
        if (bond.diedAt().isEmpty()) return false;
        long revivalCooldownMs = Config.revivalCooldownMs();
        if (revivalCooldownMs <= 0L) return false;
        return System.currentTimeMillis() - bond.diedAt().get() < revivalCooldownMs;
    }
}
