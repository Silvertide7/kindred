package net.silvertide.petsummon.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.petsummon.PetSummon;

import java.util.UUID;

/**
 * Client asks the server "can I bind this entity?" so the screen can hide the
 * Bind button for entities the client can't fully validate locally — chiefly
 * {@code AbstractHorse}, which doesn't sync owner UUID to the client.
 */
public record C2SCheckBindCandidate(UUID entityUUID) implements CustomPacketPayload {
    public static final Type<C2SCheckBindCandidate> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PetSummon.MODID, "c2s_check_bind_candidate"));

    public static final StreamCodec<ByteBuf, C2SCheckBindCandidate> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, C2SCheckBindCandidate::entityUUID,
            C2SCheckBindCandidate::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
