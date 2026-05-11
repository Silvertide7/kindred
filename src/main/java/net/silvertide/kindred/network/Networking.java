package net.silvertide.kindred.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.client.network.ClientPacketHandler;
import net.silvertide.kindred.network.packet.C2SCancelHold;
import net.silvertide.kindred.network.packet.C2SCheckBindCandidate;
import net.silvertide.kindred.network.packet.C2SClaimEntity;
import net.silvertide.kindred.network.packet.C2SOpenRoster;
import net.silvertide.kindred.network.packet.C2SRenameBond;
import net.silvertide.kindred.network.packet.C2SReorderBond;
import net.silvertide.kindred.network.packet.C2SRequestHold;
import net.silvertide.kindred.network.packet.C2SSetActivePet;
import net.silvertide.kindred.network.packet.S2CBindCandidateResult;
import net.silvertide.kindred.network.packet.S2CHoldStart;
import net.silvertide.kindred.network.packet.S2CHoldStop;
import net.silvertide.kindred.network.packet.S2CRosterSync;

@EventBusSubscriber(modid = Kindred.MODID)
public final class Networking {

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Kindred.MODID).versioned("1");

        registrar.playToServer(C2SOpenRoster.TYPE, C2SOpenRoster.STREAM_CODEC, ServerPacketHandler::onOpenRoster);
        registrar.playToServer(C2SRequestHold.TYPE, C2SRequestHold.STREAM_CODEC, ServerPacketHandler::onRequestHold);
        registrar.playToServer(C2SCancelHold.TYPE, C2SCancelHold.STREAM_CODEC, ServerPacketHandler::onCancelHold);
        registrar.playToServer(C2SClaimEntity.TYPE, C2SClaimEntity.STREAM_CODEC, ServerPacketHandler::onClaimEntity);
        registrar.playToServer(C2SSetActivePet.TYPE, C2SSetActivePet.STREAM_CODEC, ServerPacketHandler::onSetActivePet);
        registrar.playToServer(C2SRenameBond.TYPE, C2SRenameBond.STREAM_CODEC, ServerPacketHandler::onRenameBond);
        registrar.playToServer(C2SReorderBond.TYPE, C2SReorderBond.STREAM_CODEC, ServerPacketHandler::onReorderBond);
        registrar.playToServer(C2SCheckBindCandidate.TYPE, C2SCheckBindCandidate.STREAM_CODEC, ServerPacketHandler::onCheckBindCandidate);

        registrar.playToClient(S2CRosterSync.TYPE, S2CRosterSync.STREAM_CODEC, ClientPacketHandler::onRosterSync);
        registrar.playToClient(S2CHoldStart.TYPE, S2CHoldStart.STREAM_CODEC, ClientPacketHandler::onHoldStart);
        registrar.playToClient(S2CHoldStop.TYPE, S2CHoldStop.STREAM_CODEC, ClientPacketHandler::onHoldStop);
        registrar.playToClient(S2CBindCandidateResult.TYPE, S2CBindCandidateResult.STREAM_CODEC, ClientPacketHandler::onBindCandidateResult);
    }

    private Networking() {}
}
