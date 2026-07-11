package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;

public record S2CHoldStop() {
    public void encode(FriendlyByteBuf buf) {}

    public static S2CHoldStop decode(FriendlyByteBuf buf) {
        return new S2CHoldStop();
    }
}
