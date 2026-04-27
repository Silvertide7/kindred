package net.silvertide.petsummon.client.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.silvertide.petsummon.client.data.ClientRosterData;
import net.silvertide.petsummon.network.packet.S2CRosterSync;

public final class ClientPacketHandler {
    public static void onRosterSync(S2CRosterSync payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRosterData.update(payload.bonds()));
    }

    private ClientPacketHandler() {}
}
