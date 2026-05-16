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

public record C2SRequestHold(HoldManager.Action action, Optional<UUID> bondId) implements CustomPacketPayload {
    public static final Type<C2SRequestHold> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "c2s_request_hold"));

    /** Shared with {@link S2CHoldStart} so the wire format for {@code Action}
     *  stays in one place. A single byte (the enum ordinal) is plenty for four
     *  values and matches Minecraft's convention for small enums on the network. */
    public static final StreamCodec<ByteBuf, HoldManager.Action> ACTION_CODEC = ByteBufCodecs.BYTE.map(
            b -> HoldManager.Action.values()[b & 0xFF],
            a -> (byte) a.ordinal()
    );

    public static final StreamCodec<ByteBuf, C2SRequestHold> STREAM_CODEC = StreamCodec.composite(
            ACTION_CODEC, C2SRequestHold::action,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), C2SRequestHold::bondId,
            C2SRequestHold::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
