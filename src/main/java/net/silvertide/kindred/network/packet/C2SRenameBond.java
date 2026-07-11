package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Optional;
import java.util.UUID;

public record C2SRenameBond(UUID bondId, Optional<String> newName) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(bondId);
        buf.writeOptional(newName, FriendlyByteBuf::writeUtf);
    }

    public static C2SRenameBond decode(FriendlyByteBuf buf) {
        return new C2SRenameBond(buf.readUUID(), buf.readOptional(FriendlyByteBuf::readUtf));
    }
}
