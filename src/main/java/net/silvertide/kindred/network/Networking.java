package net.silvertide.kindred.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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

public final class Networking {
    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Kindred.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register() {
        int id = 0;

        CHANNEL.messageBuilder(C2SOpenRoster.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SOpenRoster::encode).decoder(C2SOpenRoster::decode)
                .consumerMainThread(ServerPacketHandler::onOpenRoster).add();
        CHANNEL.messageBuilder(C2SRequestHold.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SRequestHold::encode).decoder(C2SRequestHold::decode)
                .consumerMainThread(ServerPacketHandler::onRequestHold).add();
        CHANNEL.messageBuilder(C2SCancelHold.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SCancelHold::encode).decoder(C2SCancelHold::decode)
                .consumerMainThread(ServerPacketHandler::onCancelHold).add();
        CHANNEL.messageBuilder(C2SClaimEntity.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SClaimEntity::encode).decoder(C2SClaimEntity::decode)
                .consumerMainThread(ServerPacketHandler::onClaimEntity).add();
        CHANNEL.messageBuilder(C2SSetActivePet.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SSetActivePet::encode).decoder(C2SSetActivePet::decode)
                .consumerMainThread(ServerPacketHandler::onSetActivePet).add();
        CHANNEL.messageBuilder(C2SRenameBond.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SRenameBond::encode).decoder(C2SRenameBond::decode)
                .consumerMainThread(ServerPacketHandler::onRenameBond).add();
        CHANNEL.messageBuilder(C2SReorderBond.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SReorderBond::encode).decoder(C2SReorderBond::decode)
                .consumerMainThread(ServerPacketHandler::onReorderBond).add();
        CHANNEL.messageBuilder(C2SCheckBindCandidate.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SCheckBindCandidate::encode).decoder(C2SCheckBindCandidate::decode)
                .consumerMainThread(ServerPacketHandler::onCheckBindCandidate).add();

        CHANNEL.messageBuilder(S2CRosterSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CRosterSync::encode).decoder(S2CRosterSync::decode)
                .consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onRosterSync(msg, ctx))).add();
        CHANNEL.messageBuilder(S2CHoldStart.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CHoldStart::encode).decoder(S2CHoldStart::decode)
                .consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onHoldStart(msg, ctx))).add();
        CHANNEL.messageBuilder(S2CHoldStop.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CHoldStop::encode).decoder(S2CHoldStop::decode)
                .consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onHoldStop(msg, ctx))).add();
        CHANNEL.messageBuilder(S2CBindCandidateResult.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBindCandidateResult::encode).decoder(S2CBindCandidateResult::decode)
                .consumerMainThread((msg, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.onBindCandidateResult(msg, ctx))).add();
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    private Networking() {}
}
