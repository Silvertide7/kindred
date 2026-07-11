package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Optional;
import java.util.UUID;

public record S2CBindCandidateResult(
        UUID entityUUID,
        boolean canBind,
        Optional<String> denyMessageKey
) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(entityUUID);
        buf.writeBoolean(canBind);
        buf.writeOptional(denyMessageKey, FriendlyByteBuf::writeUtf);
    }

    public static S2CBindCandidateResult decode(FriendlyByteBuf buf) {
        return new S2CBindCandidateResult(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readOptional(FriendlyByteBuf::readUtf));
    }
}
