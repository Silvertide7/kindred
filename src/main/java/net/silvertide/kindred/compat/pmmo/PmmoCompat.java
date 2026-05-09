package net.silvertide.kindred.compat.pmmo;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.silvertide.kindred.Kindred;

public final class PmmoCompat {
    private static final PmmoBridge BRIDGE;

    static {
        PmmoBridge bridge = null;
        if (ModList.get().isLoaded("pmmo")) {
            try {
                bridge = (PmmoBridge) Class.forName("net.silvertide.kindred.compat.pmmo.PmmoBridgeImpl")
                        .getDeclaredConstructor()
                        .newInstance();
                Kindred.LOGGER.info("[kindred] PMMO compat bridge initialized.");
            } catch (Throwable t) {
                Kindred.LOGGER.error("[kindred] Failed to initialize PMMO compat bridge", t);
            }
        }
        BRIDGE = bridge;
    }

    public static boolean isAvailable() {
        return BRIDGE != null;
    }

    public static long getSkillLevel(Player player, String skill) {
        return BRIDGE != null ? BRIDGE.getSkillLevel(player, skill) : 0L;
    }

    private PmmoCompat() {}
}
