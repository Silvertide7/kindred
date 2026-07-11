package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record C2SCheckBindCandidate(UUID entityUUID) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(entityUUID);
    }

    public static C2SCheckBindCandidate decode(FriendlyByteBuf buf) {
        return new C2SCheckBindCandidate(buf.readUUID());
    }
}
