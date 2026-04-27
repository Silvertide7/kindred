package net.silvertide.petsummon.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.petsummon.PetSummon;

import java.util.UUID;

public record C2SBreakBond(UUID bondId) implements CustomPacketPayload {
    public static final Type<C2SBreakBond> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PetSummon.MODID, "c2s_break_bond"));

    public static final StreamCodec<ByteBuf, C2SBreakBond> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, C2SBreakBond::bondId,
            C2SBreakBond::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
