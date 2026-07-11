package net.silvertide.kindred.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.silvertide.kindred.network.BondView;

import java.util.List;

public record S2CRosterSync(List<BondView> bonds, long globalCooldownRemainingMs, int effectiveMaxBonds) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(bonds, (b, view) -> view.encode(b));
        buf.writeVarLong(globalCooldownRemainingMs);
        buf.writeVarInt(effectiveMaxBonds);
    }

    public static S2CRosterSync decode(FriendlyByteBuf buf) {
        return new S2CRosterSync(
                buf.readList(BondView::decode),
                buf.readVarLong(),
                buf.readVarInt());
    }
}
