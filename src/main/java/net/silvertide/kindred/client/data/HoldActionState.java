package net.silvertide.kindred.client.data;

import net.minecraft.client.Minecraft;
import net.silvertide.kindred.bond.HoldManager;

import java.util.Optional;
import java.util.UUID;

public final class HoldActionState {

    private static HoldManager.Action action;
    private static UUID bondId;
    private static long startTick;
    private static long endTick;

    public static void applyServerStart(HoldManager.Action action,
                                        Optional<UUID> bondId,
                                        long startTick,
                                        long endTick) {
        HoldActionState.action = action;
        HoldActionState.bondId = bondId.orElse(null);
        HoldActionState.startTick = startTick;
        HoldActionState.endTick = endTick;
    }

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
