package net.silvertide.kindred.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.kindred.Kindred;

public record C2SCancelHold() implements CustomPacketPayload {
    public static final Type<C2SCancelHold> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "c2s_cancel_hold"));

    public static final StreamCodec<ByteBuf, C2SCancelHold> STREAM_CODEC =
            StreamCodec.unit(new C2SCancelHold());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
