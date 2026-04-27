package net.silvertide.petsummon.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.petsummon.PetSummon;

public record C2SSummonByKeybind() implements CustomPacketPayload {
    public static final Type<C2SSummonByKeybind> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PetSummon.MODID, "c2s_summon_by_keybind"));

    public static final StreamCodec<ByteBuf, C2SSummonByKeybind> STREAM_CODEC =
            StreamCodec.unit(new C2SSummonByKeybind());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
