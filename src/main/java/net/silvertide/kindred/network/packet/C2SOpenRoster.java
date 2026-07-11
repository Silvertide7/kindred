package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;

public record C2SOpenRoster() {
    public void encode(FriendlyByteBuf buf) {}

    public static C2SOpenRoster decode(FriendlyByteBuf buf) {
        return new C2SOpenRoster();
    }
}
