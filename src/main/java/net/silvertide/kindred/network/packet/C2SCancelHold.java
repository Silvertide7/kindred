package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;

public record C2SCancelHold() {
    public void encode(FriendlyByteBuf buf) {}

    public static C2SCancelHold decode(FriendlyByteBuf buf) {
        return new C2SCancelHold();
    }
}
