package net.silvertide.kindred.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record BondRoster(Map<UUID, Bond> bonds, Optional<UUID> activePetId) {
    public static final BondRoster EMPTY = new BondRoster(Map.of(), Optional.empty());

    public static final Codec<BondRoster> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Bond.CODEC).fieldOf("bonds").forGetter(BondRoster::bonds),
            UUIDUtil.STRING_CODEC.optionalFieldOf("active").forGetter(BondRoster::activePetId)
    ).apply(instance, BondRoster::new));

    public Optional<Bond> get(UUID bondId) {
        return Optional.ofNullable(bonds.get(bondId));
    }

    public BondRoster with(Bond bond) {
        Map<UUID, Bond> next = new LinkedHashMap<>(bonds);
        next.put(bond.bondId(), bond);
        return new BondRoster(next, activePetId);
    }

    public BondRoster without(UUID bondId) {
        if (!bonds.containsKey(bondId)) return this;
        Map<UUID, Bond> next = new LinkedHashMap<>(bonds);
        next.remove(bondId);
        // If the broken bond was the active one, promote the oldest remaining bond
        // (if any). Keeps the invariant: bonds non-empty ⇒ active is set.
        Optional<UUID> nextActive;
        if (activePetId.isPresent() && activePetId.get().equals(bondId)) {
            nextActive = oldestBondId(next);
        } else {
            nextActive = activePetId;
        }
        return new BondRoster(next, nextActive);
    }

    private static Optional<UUID> oldestBondId(Map<UUID, Bond> bonds) {
        return bonds.values().stream()
                .min(Comparator.comparingLong(Bond::bondedAt))
                .map(Bond::bondId);
    }

    /**
     * Returns a roster with the given bondId set as active. Constraints:
     * <ul>
     *   <li>If the bondId isn't present in this roster, returns this unchanged.</li>
     *   <li>Empty Optional is rejected (returns this unchanged) when the roster has
     *       bonds — preserves the invariant that bonds non-empty ⇒ active is set.
     *       Clearing only succeeds when there are no bonds left.</li>
     * </ul>
     */
    public BondRoster withActive(Optional<UUID> bondId) {
        if (bondId.isPresent() && !bonds.containsKey(bondId.get())) return this;
        if (bondId.isEmpty() && !bonds.isEmpty()) return this;
        return new BondRoster(bonds, bondId);
    }

    public boolean isActive(UUID bondId) {
        return activePetId.map(id -> id.equals(bondId)).orElse(false);
    }

    public int size() {
        return bonds.size();
    }

    /**
     * Returns a roster with {@code bondId} moved by {@code delta} positions in the
     * bond order (negative = up, positive = down). Clamps to valid index bounds.
     * The roster's {@link Map} is a {@link LinkedHashMap} and the codec preserves
     * iteration order across save/load, so this is the source of truth for screen
     * row order.
     */
    public BondRoster withMoved(UUID bondId, int delta) {
        if (!bonds.containsKey(bondId) || delta == 0) return this;
        List<UUID> ids = new ArrayList<>(bonds.keySet());
        int idx = ids.indexOf(bondId);
        int newIdx = Math.max(0, Math.min(ids.size() - 1, idx + delta));
        if (idx == newIdx) return this;
        ids.remove(idx);
        ids.add(newIdx, bondId);
        Map<UUID, Bond> reordered = new LinkedHashMap<>();
        for (UUID id : ids) reordered.put(id, bonds.get(id));
        return new BondRoster(reordered, activePetId);
    }
}
