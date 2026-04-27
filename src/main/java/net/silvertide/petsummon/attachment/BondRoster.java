package net.silvertide.petsummon.attachment;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record BondRoster(Map<UUID, Bond> bonds) {
    public static final BondRoster EMPTY = new BondRoster(Map.of());

    public static final Codec<BondRoster> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Bond.CODEC)
            .xmap(BondRoster::new, BondRoster::bonds);

    public Optional<Bond> get(UUID bondId) {
        return Optional.ofNullable(bonds.get(bondId));
    }

    public BondRoster with(Bond bond) {
        Map<UUID, Bond> next = new LinkedHashMap<>(bonds);
        next.put(bond.bondId(), bond);
        return new BondRoster(next);
    }

    public BondRoster without(UUID bondId) {
        if (!bonds.containsKey(bondId)) return this;
        Map<UUID, Bond> next = new LinkedHashMap<>(bonds);
        next.remove(bondId);
        return new BondRoster(next);
    }

    public int size() {
        return bonds.size();
    }
}
