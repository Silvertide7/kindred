package net.silvertide.kindred.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.kindred.Kindred;

public record S2CHoldStop() implements CustomPacketPayload {
    public static final Type<S2CHoldStop> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "s2c_hold_stop"));

    public static final StreamCodec<ByteBuf, S2CHoldStop> STREAM_CODEC =
            StreamCodec.unit(new S2CHoldStop());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
