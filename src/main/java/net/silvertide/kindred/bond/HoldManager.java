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

/**
 * Server-authoritative hold lifecycle. Mirrors Homebound's {@code WarpManager}:
 * a single map keyed by player UUID, a server-tick driver that detects
 * completions, and one cancel path that always pushes a stop packet so the
 * client clears its bar.
 *
 * <p><b>Time</b> is measured in {@link ServerLevel#getGameTime()} ticks, not
 * wall clock — so a hold naturally pauses with the world clock in single-player
 * (server tick stops → completion doesn't fire → client gameTime stops → bar
 * freezes).</p>
 *
 * <p><b>Lifecycle</b>:
 * <ol>
 *   <li>Client sends {@code C2SRequestHold} → {@link #requestStart} validates via
 *       {@link HoldEligibility} and either registers the hold + pushes
 *       {@link S2CHoldStart}, or shows the player a vanilla action-bar deny.</li>
 *   <li>{@link #tickAll} runs each server tick; entries past their {@code endTick}
 *       fire their action via {@link BondService} and are cleared (along with a
 *       {@link S2CHoldStop} so the client bar disappears).</li>
 *   <li>{@link #cancel} handles every other removal path — damage, death, logout,
 *       client release, server stop — always pushing {@code S2CHoldStop}.</li>
 * </ol>
 */
public final class HoldManager {
    private static final HoldManager INSTANCE = new HoldManager();
    private HoldManager() {}

    public static HoldManager get() {
        return INSTANCE;
    }

    /** The four player-initiated hold-to-confirm actions Kindred supports. */
    public enum Action { SUMMON_KEYBIND, SUMMON_BOND, DISMISS, BREAK }

    /**
     * A registered hold for one player.
     *
     * @param action     which action fires on completion.
     * @param bondId     target bond — {@code null} only for {@link Action#SUMMON_KEYBIND}
     *                   (which resolves the active pet at completion time, since the
     *                   active pointer could change mid-hold via the Set Active button).
     * @param startTick  server's {@code getGameTime()} when the hold began.
     * @param endTick    server's {@code getGameTime()} when the hold should complete.
     */
    public record ActiveHold(Action action, UUID bondId, long startTick, long endTick) {}

    /** ConcurrentHashMap because the damage handler can fire from any thread that
     *  posts a damage event; mutations always happen inside server-tick or
     *  enqueueWork callbacks so single-thread access is the common case. */
    private final Map<UUID, ActiveHold> activeHoldsByPlayer = new ConcurrentHashMap<>();

    /** Cheap membership check used by the damage handler to skip the cancel path
     *  for players who aren't holding (the common case). */
    public boolean isHolding(UUID playerId) {
        return activeHoldsByPlayer.containsKey(playerId);
    }

    /**
     * Validate the request and either register a new hold (pushing {@link S2CHoldStart}
     * to the client) or surface a deny via vanilla
     * {@link ServerPlayer#displayClientMessage(Component, boolean)}.
     *
     * <p>If the player already has a hold registered, it's silently overwritten.
     * Shouldn't happen given the client invariant of one hold at a time, but if a
     * desync ever lands a stale entry, "fresh request implies fresh hold" is the
     * correct recovery — the client will receive a new start packet that overwrites
     * any stale display state.</p>
     */
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

    /**
     * Remove the player's hold and push {@link S2CHoldStop}. Used by every
     * external cancel path: damage, death, logout, client release, server stop.
     * Idempotent — calling on a player with no entry is a silent no-op (which is
     * what the damage handler relies on so it doesn't have to pre-check).
     */
    public void cancel(ServerPlayer player) {
        if (activeHoldsByPlayer.remove(player.getUUID()) == null) return;
        PacketDistributor.sendToPlayer(player, new S2CHoldStop());
    }

    /**
     * Per-tick scan for completions. Driven from {@code ServerTickEvent.Post} in
     * {@code PlayerEvents}. Bails immediately when the map is empty (the steady
     * state on any server). For each entry past its end tick, fires the action
     * and pushes {@link S2CHoldStop} so the client clears its bar — matches the
     * shape of Homebound's {@code warpPlayerHome → cancelWarp} call chain.
     */
    public void tickAll(MinecraftServer server) {
        if (activeHoldsByPlayer.isEmpty()) return;
        Iterator<Map.Entry<UUID, ActiveHold>> iterator = activeHoldsByPlayer.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveHold> entry = iterator.next();
            ActiveHold hold = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                // Player offline (rare race — logout cancel didn't fire, or the
                // entry survived a reconnect somehow). Drop the entry silently.
                iterator.remove();
                continue;
            }
            if (player.serverLevel().getGameTime() < hold.endTick()) continue;

            // Remove first so any re-entrant queries (e.g. summon → teleport
            // events → damage handler) see no hold and don't double-cancel.
            iterator.remove();
            executeAction(player, hold);
            PacketDistributor.sendToPlayer(player, new S2CHoldStop());
        }
    }

    /** Wipe all entries. Called on server stop so a singleton state can't leak
     *  across world reloads in single-player. */
    public void clear() {
        activeHoldsByPlayer.clear();
    }

    /**
     * Run the action that just completed. Each branch maps the {@link Action} enum
     * onto the corresponding {@link BondService} call and triggers the roster
     * sync that updates the client's screen state.
     */
    private void executeAction(ServerPlayer player, ActiveHold hold) {
        switch (hold.action()) {
            case SUMMON_KEYBIND -> {
                // Re-resolve the active pet at completion time. The Set Active
                // button could have changed the pointer mid-hold, and we want the
                // freshest target to fire — not whatever was active when the
                // request packet arrived.
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

    /**
     * Summon path shared by {@code SUMMON_KEYBIND} and {@code SUMMON_BOND}. Maps
     * the {@link SummonResult} into a chat message (when applicable) and
     * triggers a roster sync on success so the client sees the updated cooldown
     * state.
     */
    private void executeSummon(ServerPlayer player, UUID bondId) {
        SummonResult result = BondService.summon(player, bondId);
        chatMessageFor(result).ifPresent(player::sendSystemMessage);
        if (isSummonSuccess(result)) {
            ServerPacketHandler.sendRosterSync(player);
        }
    }

    /**
     * Maps a {@link SummonResult} to a translatable chat message, or empty for
     * results that should be silent. Successes produce no message — the pet
     * appearing in-world is its own feedback.
     */
    private static Optional<Component> chatMessageFor(SummonResult result) {
        String translationKey = switch (result) {
            case BANNED_DIMENSION -> "kindred.summon.banned_dimension";
            case BANNED_BIOME -> "kindred.summon.banned_biome";
            case ON_COOLDOWN -> "kindred.summon.on_cooldown";
            case GLOBAL_COOLDOWN -> "kindred.summon.global_cooldown";
            case REVIVAL_PENDING -> "kindred.summon.reviving";
            case NO_SPACE -> "kindred.summon.no_space";
            case PLAYER_AIRBORNE -> "kindred.summon.player_airborne";
            case CROSS_DIM_BLOCKED -> "kindred.summon.cross_dim_blocked";
            // Silent: WALKING / TELEPORTED_NEAR / SUMMONED_FRESH are visible
            // successes; NO_SUCH_BOND / SPAWN_FAILED shouldn't reach here past
            // eligibility, and SPAWN_FAILED is unactionable for the player anyway.
            case WALKING, TELEPORTED_NEAR, SUMMONED_FRESH, NO_SUCH_BOND, SPAWN_FAILED -> null;
        };
        return Optional.ofNullable(translationKey).map(Component::translatable);
    }

    /** True for the three result values that mean "a pet actually arrived." */
    private static boolean isSummonSuccess(SummonResult result) {
        return result == SummonResult.WALKING
                || result == SummonResult.TELEPORTED_NEAR
                || result == SummonResult.SUMMONED_FRESH;
    }
}
