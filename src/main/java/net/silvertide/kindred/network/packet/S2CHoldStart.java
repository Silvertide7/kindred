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

/**
 * Server confirms a hold has begun. The client populates
 * {@code HoldActionState} from this payload and renders the bar as
 * {@code (level.getGameTime() - startTick) / (endTick - startTick)}, with the
 * per-frame partial tick added on for smoothness.
 *
 * <p>The {@code (startTick, endTick)} timestamp pattern (rather than a single
 * "duration" value) mirrors Homebound's {@code CB_SyncWarpScheduleMessage} and
 * has two nice properties: it pauses naturally with the game clock in
 * single-player, and the client never has to record its own receive time.</p>
 *
 * @param action     which action is being held (drives the HUD label).
 * @param bondId     target bond — empty only for {@link HoldManager.Action#SUMMON_KEYBIND}.
 * @param startTick  server's {@code getGameTime()} when the hold began.
 * @param endTick    server's {@code getGameTime()} when it will complete.
 */
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
