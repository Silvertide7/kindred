package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record C2SReorderBond(UUID bondId, int delta) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(bondId);
        buf.writeVarInt(delta);
    }

    public static C2SReorderBond decode(FriendlyByteBuf buf) {
        return new C2SReorderBond(buf.readUUID(), buf.readVarInt());
    }
}
