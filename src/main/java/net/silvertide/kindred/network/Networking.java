package net.silvertide.kindred.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.client.network.ClientPacketHandler;
import net.silvertide.kindred.network.packet.C2SBreakBond;
import net.silvertide.kindred.network.packet.C2SCheckBindCandidate;
import net.silvertide.kindred.network.packet.C2SClaimEntity;
import net.silvertide.kindred.network.packet.C2SDismissBond;
import net.silvertide.kindred.network.packet.C2SOpenRoster;
import net.silvertide.kindred.network.packet.C2SRenameBond;
import net.silvertide.kindred.network.packet.C2SReorderBond;
import net.silvertide.kindred.network.packet.C2SSetActivePet;
import net.silvertide.kindred.network.packet.C2SSummonBond;
import net.silvertide.kindred.network.packet.C2SSummonByKeybind;
import net.silvertide.kindred.network.packet.S2CBindCandidateResult;
import net.silvertide.kindred.network.packet.S2CCancelHold;
import net.silvertide.kindred.network.packet.S2CRosterSync;

@EventBusSubscriber(modid = Kindred.MODID)
public final class Networking {

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Kindred.MODID).versioned("1");

        registrar.playToServer(C2SOpenRoster.TYPE, C2SOpenRoster.STREAM_CODEC, ServerPacketHandler::onOpenRoster);
        registrar.playToServer(C2SSummonByKeybind.TYPE, C2SSummonByKeybind.STREAM_CODEC, ServerPacketHandler::onSummonByKeybind);
        registrar.playToServer(C2SSummonBond.TYPE, C2SSummonBond.STREAM_CODEC, ServerPacketHandler::onSummonBond);
        registrar.playToServer(C2SBreakBond.TYPE, C2SBreakBond.STREAM_CODEC, ServerPacketHandler::onBreakBond);
        registrar.playToServer(C2SClaimEntity.TYPE, C2SClaimEntity.STREAM_CODEC, ServerPacketHandler::onClaimEntity);
        registrar.playToServer(C2SSetActivePet.TYPE, C2SSetActivePet.STREAM_CODEC, ServerPacketHandler::onSetActivePet);
        registrar.playToServer(C2SDismissBond.TYPE, C2SDismissBond.STREAM_CODEC, ServerPacketHandler::onDismissBond);
        registrar.playToServer(C2SRenameBond.TYPE, C2SRenameBond.STREAM_CODEC, ServerPacketHandler::onRenameBond);
        registrar.playToServer(C2SReorderBond.TYPE, C2SReorderBond.STREAM_CODEC, ServerPacketHandler::onReorderBond);
        registrar.playToServer(C2SCheckBindCandidate.TYPE, C2SCheckBindCandidate.STREAM_CODEC, ServerPacketHandler::onCheckBindCandidate);

        registrar.playToClient(S2CRosterSync.TYPE, S2CRosterSync.STREAM_CODEC, ClientPacketHandler::onRosterSync);
        registrar.playToClient(S2CCancelHold.TYPE, S2CCancelHold.STREAM_CODEC, ClientPacketHandler::onCancelHold);
        registrar.playToClient(S2CBindCandidateResult.TYPE, S2CBindCandidateResult.STREAM_CODEC, ClientPacketHandler::onBindCandidateResult);
    }

    private Networking() {}
}
