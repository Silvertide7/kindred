package net.silvertide.kindred.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public record Bond(
        UUID bondId,
        ResourceLocation entityType,
        CompoundTag nbtSnapshot,
        ResourceKey<Level> lastSeenDim,
        Vec3 lastSeenPos,
        int revision,
        Optional<String> displayName,
        long bondedAt,
        long lastSummonedAt,
        Optional<Long> diedAt
) {
    public static final Codec<Bond> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("bond_id").forGetter(Bond::bondId),
            ResourceLocation.CODEC.fieldOf("entity_type").forGetter(Bond::entityType),
            CompoundTag.CODEC.fieldOf("nbt").forGetter(Bond::nbtSnapshot),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dim").forGetter(Bond::lastSeenDim),
            Vec3.CODEC.fieldOf("pos").forGetter(Bond::lastSeenPos),
            Codec.INT.fieldOf("revision").forGetter(Bond::revision),
            Codec.STRING.optionalFieldOf("display_name").forGetter(Bond::displayName),
            Codec.LONG.fieldOf("bonded_at").forGetter(Bond::bondedAt),
            Codec.LONG.fieldOf("last_summoned_at").forGetter(Bond::lastSummonedAt),
            Codec.LONG.optionalFieldOf("died_at").forGetter(Bond::diedAt)
    ).apply(instance, Bond::new));

    public Bond withRevision(int newRevision) {
        return new Bond(bondId, entityType, nbtSnapshot, lastSeenDim, lastSeenPos, newRevision, displayName, bondedAt, lastSummonedAt, diedAt);
    }

    public Bond withSnapshot(CompoundTag newNbt, ResourceKey<Level> newDim, Vec3 newPos) {
        return new Bond(bondId, entityType, newNbt, newDim, newPos, revision, displayName, bondedAt, lastSummonedAt, diedAt);
    }

    public Bond withDisplayName(Optional<String> newName) {
        return new Bond(bondId, entityType, nbtSnapshot, lastSeenDim, lastSeenPos, revision, newName, bondedAt, lastSummonedAt, diedAt);
    }

    public Bond withLastSummonedAt(long timestampMs) {
        return new Bond(bondId, entityType, nbtSnapshot, lastSeenDim, lastSeenPos, revision, displayName, bondedAt, timestampMs, diedAt);
    }

    public Bond withDiedAt(Optional<Long> newDiedAt) {
        return new Bond(bondId, entityType, nbtSnapshot, lastSeenDim, lastSeenPos, revision, displayName, bondedAt, lastSummonedAt, newDiedAt);
    }
}
