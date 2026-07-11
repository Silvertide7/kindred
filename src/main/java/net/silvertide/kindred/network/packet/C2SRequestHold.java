package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.silvertide.kindred.bond.HoldManager;

import java.util.Optional;
import java.util.UUID;

public record C2SRequestHold(HoldManager.Action action, Optional<UUID> bondId) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeOptional(bondId, FriendlyByteBuf::writeUUID);
    }

    public static C2SRequestHold decode(FriendlyByteBuf buf) {
        return new C2SRequestHold(
                buf.readEnum(HoldManager.Action.class),
                buf.readOptional(FriendlyByteBuf::readUUID));
    }
}
