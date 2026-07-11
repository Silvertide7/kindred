package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.silvertide.kindred.bond.HoldManager;

import java.util.Optional;
import java.util.UUID;

public record S2CHoldStart(HoldManager.Action action, Optional<UUID> bondId, long startTick, long endTick) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeOptional(bondId, FriendlyByteBuf::writeUUID);
        buf.writeVarLong(startTick);
        buf.writeVarLong(endTick);
    }

    public static S2CHoldStart decode(FriendlyByteBuf buf) {
        return new S2CHoldStart(
                buf.readEnum(HoldManager.Action.class),
                buf.readOptional(FriendlyByteBuf::readUUID),
                buf.readVarLong(),
                buf.readVarLong());
    }
}
