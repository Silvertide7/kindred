package net.silvertide.kindred.bond;

import net.silvertide.kindred.config.Config;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalSummonCooldownTracker {
    private static final GlobalSummonCooldownTracker INSTANCE = new GlobalSummonCooldownTracker();
    private GlobalSummonCooldownTracker() {}

    public static GlobalSummonCooldownTracker get() {
        return INSTANCE;
    }

    private final Map<UUID, Long> lastSummonMs = new ConcurrentHashMap<>();

    public void recordSummon(UUID playerId) {
        long now = System.currentTimeMillis();
        // Sweep stale entries on write — covers players who summoned once long ago
        // and never summoned again, so their entry was never read into expiry.
        // Inexpensive: O(n) on a map that's already kept small by self-pruning.
        long staleCutoff = now - Config.summonGlobalCooldownMs();
        lastSummonMs.entrySet().removeIf(e -> e.getValue() < staleCutoff);
        lastSummonMs.put(playerId, now);
    }

    /** Returns 0 if no cooldown is active (or config is disabled). When the cooldown
     *  has fully elapsed, the player's entry is removed as a side effect — keeps the
     *  map drained without an explicit cleanup pass. */
    public long remainingMs(UUID playerId, long cooldownMs) {
        if (cooldownMs <= 0L) return 0L;
        Long last = lastSummonMs.get(playerId);
        if (last == null) return 0L;
        long elapsed = System.currentTimeMillis() - last;
        if (elapsed >= cooldownMs) {
            // Entry no longer enforces anything. Remove only if the value hasn't
            // changed under us — guards against racing with a recordSummon that
            // landed between our get() and remove().
            lastSummonMs.remove(playerId, last);
            return 0L;
        }
        return cooldownMs - elapsed;
    }

    public void clear() {
        lastSummonMs.clear();
    }
}
