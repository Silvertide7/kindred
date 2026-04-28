package net.silvertide.kindred.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.kindred.Kindred;

/**
 * Server tells the client to cancel any in-progress hold (keybind hold or screen
 * row-button hold). Fired from the damage handler when the owning player is hurt.
 */
public record S2CCancelHold() implements CustomPacketPayload {
    public static final Type<S2CCancelHold> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "s2c_cancel_hold"));

    public static final StreamCodec<ByteBuf, S2CCancelHold> STREAM_CODEC =
            StreamCodec.unit(new S2CCancelHold());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
