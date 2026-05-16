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
