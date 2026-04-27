package net.silvertide.petsummon.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record Bonded(UUID bondId, UUID ownerUUID, int revision) {
    // Sentinel returned by AttachmentType's default supplier when an entity has no Bonded data.
    // Callers must check entity.hasData(...) before treating data as real.
    public static final Bonded EMPTY = new Bonded(new UUID(0L, 0L), new UUID(0L, 0L), 0);

    public static final Codec<Bonded> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("bond_id").forGetter(Bonded::bondId),
            UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(Bonded::ownerUUID),
            Codec.INT.fieldOf("revision").forGetter(Bonded::revision)
    ).apply(instance, Bonded::new));

    public Bonded withRevision(int newRevision) {
        return new Bonded(bondId, ownerUUID, newRevision);
    }
}
