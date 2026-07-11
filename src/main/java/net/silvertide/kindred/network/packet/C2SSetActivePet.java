package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Optional;
import java.util.UUID;

public record C2SSetActivePet(Optional<UUID> bondId) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeOptional(bondId, FriendlyByteBuf::writeUUID);
    }

    public static C2SSetActivePet decode(FriendlyByteBuf buf) {
        return new C2SSetActivePet(buf.readOptional(FriendlyByteBuf::readUUID));
    }
}
