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
        long staleCutoff = now - Config.summonGlobalCooldownMs();
        lastSummonMs.entrySet().removeIf(e -> e.getValue() < staleCutoff);
        lastSummonMs.put(playerId, now);
    }

    public long remainingMs(UUID playerId, long cooldownMs) {
        if (cooldownMs <= 0L) return 0L;
        Long last = lastSummonMs.get(playerId);
        if (last == null) return 0L;
        long elapsed = System.currentTimeMillis() - last;
        if (elapsed >= cooldownMs) {
            lastSummonMs.remove(playerId, last);
            return 0L;
        }
        return cooldownMs - elapsed;
    }

    public void clear() {
        lastSummonMs.clear();
    }
}
