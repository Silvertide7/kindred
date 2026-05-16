package net.silvertide.kindred.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.kindred.Kindred;

import java.util.Optional;
import java.util.UUID;

public record C2SRenameBond(UUID bondId, Optional<String> newName) implements CustomPacketPayload {
    public static final Type<C2SRenameBond> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "c2s_rename_bond"));

    public static final StreamCodec<ByteBuf, C2SRenameBond> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, C2SRenameBond::bondId,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), C2SRenameBond::newName,
            C2SRenameBond::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
