package net.silvertide.petsummon.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.petsummon.PetSummon;

import java.util.Optional;
import java.util.UUID;

/**
 * Server's verdict on whether the given entity is bindable for the requesting
 * player. The {@code entityUUID} echoes the request so a stale response can't
 * flip a button for a different candidate. When {@code canBind} is false,
 * {@code denyMessageKey} optionally carries a translation key explaining why,
 * which the screen renders in place of the generic bind hint. Empty key with
 * {@code canBind=false} = silent rejection (e.g. entity too far / not found).
 */
public record S2CBindCandidateResult(
        UUID entityUUID,
        boolean canBind,
        Optional<String> denyMessageKey
) implements CustomPacketPayload {
    public static final Type<S2CBindCandidateResult> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PetSummon.MODID, "s2c_bind_candidate_result"));

    public static final StreamCodec<ByteBuf, S2CBindCandidateResult> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, S2CBindCandidateResult::entityUUID,
            ByteBufCodecs.BOOL, S2CBindCandidateResult::canBind,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), S2CBindCandidateResult::denyMessageKey,
            S2CBindCandidateResult::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
