package net.silvertide.kindred.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.kindred.Kindred;

/**
 * Server tells the client to clear its hold display. Sent on every removal
 * path — damage cancel, client-release cancel, death, logout, server stop,
 * <em>and</em> completion. Unlike Homebound (which uses a sentinel
 * {@code (0, 0)} timestamp in the start packet), Kindred uses an explicit stop
 * packet for clearer intent and a slightly smaller payload.
 *
 * <p>The client handler clears {@code HoldActionState} (which hides the HUD bar)
 * and the screen's local press tracker (so a stale press doesn't linger after
 * the server cancelled).</p>
 */
public record S2CHoldStop() implements CustomPacketPayload {
    public static final Type<S2CHoldStop> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "s2c_hold_stop"));

    public static final StreamCodec<ByteBuf, S2CHoldStop> STREAM_CODEC =
            StreamCodec.unit(new S2CHoldStop());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
