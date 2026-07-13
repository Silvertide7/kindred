package net.silvertide.kindred.client.data;

import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.network.BondView;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class ClientRosterData {
    private static List<BondView> bonds = Collections.emptyList();
    private static long lastUpdatedClientMs = 0L;
    private static long globalCooldownRemainingMsAtReceive = 0L;
    private static int effectiveMaxBonds = -1;

    public static void update(List<BondView> newBonds, long globalCooldownRemainingMs, int effectiveMaxBondsValue) {
        bonds = List.copyOf(newBonds);
        lastUpdatedClientMs = System.currentTimeMillis();
        globalCooldownRemainingMsAtReceive = globalCooldownRemainingMs;
        effectiveMaxBonds = effectiveMaxBondsValue;
    }

    public static int effectiveMaxBonds() {
        return effectiveMaxBonds < 0 ? Config.STARTING_COMPANION_BONDS.get() : effectiveMaxBonds;
    }

    public static List<BondView> bonds() {
        return bonds;
    }

    public static Optional<BondView> findActive() {
        return bonds.stream().filter(BondView::isActive).findFirst();
    }

    public static boolean isOnCooldown(BondView bond) {
        long elapsedSinceReceive = System.currentTimeMillis() - lastUpdatedClientMs;
        return elapsedSinceReceive < bond.cooldownRemainingMs();
    }

    public static long bondCooldownRemainingMsNow(BondView bond) {
        long elapsedSinceReceive = System.currentTimeMillis() - lastUpdatedClientMs;
        return Math.max(0L, bond.cooldownRemainingMs() - elapsedSinceReceive);
    }

    public static boolean isRevivalPending(BondView bond) {
        return revivalRemainingMsNow(bond) > 0L;
    }

    public static long revivalRemainingMsNow(BondView bond) {
        long elapsedSinceReceive = System.currentTimeMillis() - lastUpdatedClientMs;
        return Math.max(0L, bond.revivalRemainingMs() - elapsedSinceReceive);
    }

    public static boolean isGlobalSummonOnCooldown() {
        return globalCooldownRemainingMsNow() > 0L;
    }

    public static long globalCooldownRemainingMsNow() {
        long elapsedSinceReceive = System.currentTimeMillis() - lastUpdatedClientMs;
        return Math.max(0L, globalCooldownRemainingMsAtReceive - elapsedSinceReceive);
    }

    public static void clear() {
        bonds = Collections.emptyList();
        lastUpdatedClientMs = 0L;
        globalCooldownRemainingMsAtReceive = 0L;
        effectiveMaxBonds = -1;
    }

    private ClientRosterData() {}
}
