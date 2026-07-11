package net.silvertide.kindred.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.silvertide.kindred.attachment.Bond;

import java.util.Optional;
import java.util.UUID;

public record BondView(
        UUID bondId,
        UUID entityUUID,
        ResourceLocation entityType,
        Optional<String> displayName,
        ResourceLocation lastSeenDim,
        Vec3 lastSeenPos,
        boolean isActive,
        boolean loaded,
        long cooldownRemainingMs,
        long revivalRemainingMs,
        CompoundTag nbtSnapshot
) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(bondId);
        buf.writeUUID(entityUUID);
        buf.writeResourceLocation(entityType);
        buf.writeOptional(displayName, FriendlyByteBuf::writeUtf);
        buf.writeResourceLocation(lastSeenDim);
        buf.writeDouble(lastSeenPos.x);
        buf.writeDouble(lastSeenPos.y);
        buf.writeDouble(lastSeenPos.z);
        buf.writeBoolean(isActive);
        buf.writeBoolean(loaded);
        buf.writeVarLong(cooldownRemainingMs);
        buf.writeVarLong(revivalRemainingMs);
        buf.writeNbt(nbtSnapshot);
    }

    public static BondView decode(FriendlyByteBuf buf) {
        return new BondView(
                buf.readUUID(),
                buf.readUUID(),
                buf.readResourceLocation(),
                buf.readOptional(FriendlyByteBuf::readUtf),
                buf.readResourceLocation(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readAnySizeNbt());
    }

    private static final UUID NO_UUID = new UUID(0L, 0L);

    /**
     * Build a view using a specific NBT snapshot — caller passes the live entity's
     * current NBT when the pet is loaded, so changes made while the pet is in-world
     * (saddling, putting on armor, breeding, etc.) show up in the preview without
     * waiting for the entity to leave its chunk.
     */
    public static BondView from(Bond bond, boolean isActive, boolean loaded, long cooldownRemainingMs, long revivalRemainingMs, CompoundTag nbtSnapshot) {
        UUID entityUUID = nbtSnapshot.hasUUID("UUID")
                ? nbtSnapshot.getUUID("UUID")
                : NO_UUID;
        return new BondView(
                bond.bondId(),
                entityUUID,
                bond.entityType(),
                bond.displayName(),
                bond.lastSeenDim().location(),
                bond.lastSeenPos(),
                isActive,
                loaded,
                cooldownRemainingMs,
                revivalRemainingMs,
                nbtSnapshot
        );
    }
}
