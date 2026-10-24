package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record C2SSetAutoMount(UUID bondId, boolean autoMount) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(bondId);
        buf.writeBoolean(autoMount);
    }

    public static C2SSetAutoMount decode(FriendlyByteBuf buf) {
        return new C2SSetAutoMount(buf.readUUID(), buf.readBoolean());
    }
}
