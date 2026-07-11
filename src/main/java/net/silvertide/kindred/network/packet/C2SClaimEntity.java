package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record C2SClaimEntity(UUID entityUUID) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(entityUUID);
    }

    public static C2SClaimEntity decode(FriendlyByteBuf buf) {
        return new C2SClaimEntity(buf.readUUID());
    }
}
