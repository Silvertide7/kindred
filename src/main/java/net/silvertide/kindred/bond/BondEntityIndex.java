package net.silvertide.kindred.bond;

import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Index of bonded entities currently loaded in the world keyed off bondId.
// Maintained incrementally via EntityJoinLevelEvent / EntityLeaveLevelEvent.
// Used by BondService to avoid scanning every entity in every dimension on summon.
public final class BondEntityIndex {
    private static final BondEntityIndex INSTANCE = new BondEntityIndex();
    private final Map<UUID, Entity> entitiesByBondId = new ConcurrentHashMap<>();
    private BondEntityIndex() {}

    public static BondEntityIndex get() {
        return INSTANCE;
    }

    public void track(UUID bondId, Entity entity) {
        entitiesByBondId.put(bondId, entity);
    }

    public void untrack(UUID bondId) {
        entitiesByBondId.remove(bondId);
    }

    public void untrack(UUID bondId, Entity expected) {
        entitiesByBondId.remove(bondId, expected);
    }

    public Optional<Entity> find(UUID bondId) {
        Entity entity = entitiesByBondId.get(bondId);
        if (entity == null || entity.isRemoved()) return Optional.empty();
        return Optional.of(entity);
    }

    public void clear() {
        entitiesByBondId.clear();
    }
}
