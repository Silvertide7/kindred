package net.silvertide.kindred.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.kindred.Kindred;

import java.util.UUID;

public record C2SSetAutoMount(UUID bondId, boolean autoMount) implements CustomPacketPayload {
    public static final Type<C2SSetAutoMount> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "c2s_set_auto_mount"));

    public static final StreamCodec<ByteBuf, C2SSetAutoMount> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, C2SSetAutoMount::bondId,
            ByteBufCodecs.BOOL, C2SSetAutoMount::autoMount,
            C2SSetAutoMount::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
