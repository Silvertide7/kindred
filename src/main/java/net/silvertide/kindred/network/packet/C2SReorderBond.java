package net.silvertide.kindred.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.kindred.Kindred;

import java.util.UUID;

public record C2SReorderBond(UUID bondId, int delta) implements CustomPacketPayload {
    public static final Type<C2SReorderBond> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "c2s_reorder_bond"));

    public static final StreamCodec<ByteBuf, C2SReorderBond> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, C2SReorderBond::bondId,
            ByteBufCodecs.VAR_INT, C2SReorderBond::delta,
            C2SReorderBond::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
