package net.silvertide.kindred.client.data;

import net.minecraft.client.Minecraft;
import net.silvertide.kindred.bond.HoldManager;

import java.util.Optional;
import java.util.UUID;

/**
 * Client-side passive view of the server's hold lifecycle. Mirrors Homebound's
 * {@code ClientWarpData} — it just stores the timestamps the server pushed in
 * {@link net.silvertide.kindred.network.packet.S2CHoldStart} and derives a
 * progress fraction from the live {@code level.getGameTime()}.
 *
 * <p>The client never decides anything here:
 * <ul>
 *   <li>{@code applyServerStart} is called by the {@code S2CHoldStart} handler.</li>
 *   <li>{@code applyServerStop} is called by the {@code S2CHoldStop} handler.</li>
 *   <li>Render code reads {@link #progress()} for the bar fill.</li>
 * </ul>
 *
 * <p>Static fields rather than an instance — there's exactly one local player
 * and exactly one in-flight hold at a time, so the indirection of an object
 * doesn't buy anything.</p>
 */
public final class HoldActionState {

    /** Null when no hold is active. */
    private static HoldManager.Action action;
    /** Null for {@link HoldManager.Action#SUMMON_KEYBIND} (server resolves active pet)
     *  or when no hold is active. */
    private static UUID bondId;
    /** Server's {@code getGameTime()} when the hold began. */
    private static long startTick;
    /** Server's {@code getGameTime()} when the hold should complete. */
    private static long endTick;

    /** Populate from {@code S2CHoldStart}. Overwrites any prior state — the server
     *  always sends a clean start, so a fresh start replaces whatever was there. */
    public static void applyServerStart(HoldManager.Action action,
                                        Optional<UUID> bondId,
                                        long startTick,
                                        long endTick) {
        HoldActionState.action = action;
        HoldActionState.bondId = bondId.orElse(null);
        HoldActionState.startTick = startTick;
        HoldActionState.endTick = endTick;
    }

    /** Clear all fields. Called from the {@code S2CHoldStop} handler on every
     *  removal path (cancel, completion, damage, death, etc.). */
    public static void applyServerStop() {
        action = null;
        bondId = null;
        startTick = 0L;
        endTick = 0L;
    }

    public static boolean isActive() {
        return action != null;
    }

    public static HoldManager.Action action() {
        return action;
    }

    public static UUID bondId() {
        return bondId;
    }

    /**
     * Progress in [0, 1] for the HUD bar and screen-row fill. Derived from the
     * client level's {@code getGameTime()} plus the per-frame partial-tick
     * offset — without the partial tick, gameTime only advances 20 times per
     * second and a high-fps bar visibly steps in 50ms jumps. The partial tick
     * fills in between ticks at full framerate.
     *
     * <p>During pause, both {@code gameTime} and {@code partialTick} freeze, so
     * the bar correctly stays still. Capped at 1 so a tick that runs late can't
     * overshoot the bar past the end.</p>
     */
    public static float progress() {
        if (!isActive()) return 0F;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return 0F;
        long totalTicks = endTick - startTick;
        if (totalTicks <= 0L) return 1F;
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        float elapsedTicks = (minecraft.level.getGameTime() - startTick) + partialTick;
        if (elapsedTicks <= 0F) return 0F;
        return Math.min(1F, elapsedTicks / totalTicks);
    }

    private HoldActionState() {}
}
