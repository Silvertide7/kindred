package net.silvertide.kindred.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.silvertide.kindred.bond.BondEntityIndex;
import net.silvertide.kindred.bond.GlobalSummonCooldownTracker;
import net.silvertide.kindred.bond.HoldManager;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.attachment.Bond;
import net.silvertide.kindred.attachment.Bonded;
import net.silvertide.kindred.attachment.BondRoster;
import net.silvertide.kindred.registry.ModAttachments;
import net.silvertide.kindred.registry.ModAttributes;
import net.silvertide.kindred.network.ServerPacketHandler;
import net.silvertide.kindred.data.OfflineSnapshot;
import net.silvertide.kindred.data.KindredSavedData;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = Kindred.MODID)
public final class PlayerEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        syncMaxCompanionBonds(player);

        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        if (roster.bonds().isEmpty()) return;

        KindredSavedData saved = KindredSavedData.get(level);
        BondRoster updated = roster;

        for (UUID bondId : new LinkedHashSet<>(roster.bonds().keySet())) {
            if (saved.wasKilledOffline(bondId)) {
                updated = updated.without(bondId);
                saved.clearBond(bondId);
                Kindred.LOGGER.info("[kindred] {} logged in to find bond {} died offline", player.getGameProfile().getName(), bondId);
                continue;
            }

            Optional<OfflineSnapshot> snap = saved.takeOfflineSnapshot(bondId);
            if (snap.isPresent()) {
                Optional<Bond> bond = updated.get(bondId);
                if (bond.isPresent()) {
                    OfflineSnapshot s = snap.get();
                    updated = updated.with(bond.get().withSnapshot(s.nbt(), s.dim(), s.pos()));
                }
            }

            Optional<Long> diedOffline = saved.takeDiedOffline(bondId);
            if (diedOffline.isPresent()) {
                Optional<Bond> bond = updated.get(bondId);
                if (bond.isPresent()) {
                    updated = updated.with(bond.get().withDiedAt(diedOffline));
                }
            }
        }

        if (updated != roster) {
            player.setData(ModAttachments.BOND_ROSTER.get(), updated);
        }

        ServerPacketHandler.sendRosterSync(player);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!Config.CANCEL_HOLD_ON_DAMAGE.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (HoldManager.get().isHolding(player.getUUID())) {
            HoldManager.get().cancel(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        HoldManager.get().cancel(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        flushLoadedSnapshots(player);
        HoldManager.get().cancel(player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        HoldManager.get().tickAll(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            flushLoadedSnapshots(player);
        }
        stashSnapshotsForOfflineOwners(event.getServer());
        BondEntityIndex.get().clear();
        HoldManager.get().clear();
        GlobalSummonCooldownTracker.get().clear();
    }

    private static void stashSnapshotsForOfflineOwners(MinecraftServer server) {
        BondEntityIndex.get().forEachLoaded((bondId, entity) -> {
            if (entity instanceof LivingEntity living && living.isDeadOrDying()) return;
            if (!(entity.level() instanceof ServerLevel level)) return;
            if (!entity.hasData(ModAttachments.BONDED.get())) return;
            Bonded bonded = entity.getData(ModAttachments.BONDED.get());
            if (server.getPlayerList().getPlayer(bonded.ownerUUID()) != null) return;
            KindredSavedData.get(level).putOfflineSnapshot(bondId,
                    new OfflineSnapshot(entity.saveWithoutId(new CompoundTag()), level.dimension(), entity.position()));
        });
    }

    private static void syncMaxCompanionBonds(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(ModAttributes.MAX_COMPANION_BONDS);
        if (attribute == null) return;
        double configured = Config.STARTING_COMPANION_BONDS.get();
        if (attribute.getBaseValue() != configured) {
            attribute.setBaseValue(configured);
        }
    }

    private static void flushLoadedSnapshots(ServerPlayer player) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        if (roster.bonds().isEmpty()) return;

        BondRoster updated = roster;
        for (UUID bondId : roster.bonds().keySet()) {
            Optional<Entity> entity = BondEntityIndex.get().find(bondId);
            if (entity.isEmpty()) continue;

            Entity e = entity.get();
            Optional<Bond> bond = updated.get(bondId);
            if (bond.isEmpty()) continue;

            CompoundTag nbt = e.saveWithoutId(new CompoundTag());
            updated = updated.with(bond.get().withSnapshot(nbt, e.level().dimension(), e.position()));
        }

        if (updated != roster) {
            player.setData(ModAttachments.BOND_ROSTER.get(), updated);
        }
    }

    private PlayerEvents() {}
}
