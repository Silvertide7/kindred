package net.silvertide.kindred.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.kindred.Kindred;

/**
 * Client tells the server it released a hold — key let up, mouse button let up,
 * mouse dragged off the row button, screen closed mid-hold. The server clears
 * the entry and replies with {@link S2CHoldStop}.
 *
 * <p>Idempotent on the server: a cancel with no registered hold is a silent
 * no-op. This is by design — the client always sends cancel on release without
 * gating, even in races where the hold may have already completed or been
 * cancelled by damage.</p>
 */
public record C2SCancelHold() implements CustomPacketPayload {
    public static final Type<C2SCancelHold> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "c2s_cancel_hold"));

    public static final StreamCodec<ByteBuf, C2SCancelHold> STREAM_CODEC =
            StreamCodec.unit(new C2SCancelHold());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
