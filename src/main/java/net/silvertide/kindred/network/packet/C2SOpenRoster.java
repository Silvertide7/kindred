package net.silvertide.kindred.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.kindred.Kindred;

public record C2SOpenRoster() implements CustomPacketPayload {
    public static final Type<C2SOpenRoster> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "c2s_open_roster"));

    public static final StreamCodec<ByteBuf, C2SOpenRoster> STREAM_CODEC =
            StreamCodec.unit(new C2SOpenRoster());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
