package net.silvertide.kindred.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.bond.HoldManager;

import java.util.Optional;
import java.util.UUID;

public record S2CHoldStart(HoldManager.Action action, Optional<UUID> bondId, long startTick, long endTick)
        implements CustomPacketPayload {
    public static final Type<S2CHoldStart> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "s2c_hold_start"));

    public static final StreamCodec<ByteBuf, S2CHoldStart> STREAM_CODEC = StreamCodec.composite(
            C2SRequestHold.ACTION_CODEC, S2CHoldStart::action,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), S2CHoldStart::bondId,
            ByteBufCodecs.VAR_LONG, S2CHoldStart::startTick,
            ByteBufCodecs.VAR_LONG, S2CHoldStart::endTick,
            S2CHoldStart::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
