package net.silvertide.kindred.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
import net.silvertide.kindred.attachment.BondRoster;
import net.silvertide.kindred.registry.ModAttachments;
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

        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        if (roster.bonds().isEmpty()) return;

        KindredSavedData saved = KindredSavedData.get(level);
        BondRoster updated = roster;

        for (UUID bondId : new LinkedHashSet<>(roster.bonds().keySet())) {
            // Drain killed-while-offline first — the bond is gone before any snapshot matters.
            if (saved.wasKilledOffline(bondId)) {
                updated = updated.without(bondId);
                saved.clearBond(bondId);
                Kindred.LOGGER.info("[kindred] {} logged in to find bond {} died offline", player.getGameProfile().getName(), bondId);
                continue;
            }

            // Drain offline NBT snapshot.
            Optional<OfflineSnapshot> snap = saved.takeOfflineSnapshot(bondId);
            if (snap.isPresent()) {
                Optional<Bond> bond = updated.get(bondId);
                if (bond.isPresent()) {
                    OfflineSnapshot s = snap.get();
                    updated = updated.with(bond.get().withSnapshot(s.nbt(), s.dim(), s.pos()));
                }
            }
        }

        if (updated != roster) {
            player.setData(ModAttachments.BOND_ROSTER.get(), updated);
        }

        // Push initial roster snapshot so the keybind has data before the screen opens.
        ServerPacketHandler.sendRosterSync(player);
    }

    /**
     * Cancel an in-progress hold when the player takes damage — matches vanilla
     * bow-draw / eating interrupt behavior. Gated by config so server admins can
     * disable the interrupt entirely.
     *
     * <p>The {@code isHolding} pre-check avoids the cost of any work for the 99%
     * of damaged players who aren't holding; {@link HoldManager#cancel} is also
     * idempotent so removing the guard would still be correct, just slightly
     * less efficient.</p>
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!Config.CANCEL_HOLD_ON_DAMAGE.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (HoldManager.get().isHolding(player.getUUID())) {
            HoldManager.get().cancel(player);
        }
    }

    /**
     * Cancel any in-progress hold when the player dies. Catches death paths that
     * bypass {@code LivingDamageEvent.Pre} — {@code /kill}, void damage,
     * instant-kill sources — and is harmless when the damage handler already
     * cancelled (cancel is idempotent).
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        HoldManager.get().cancel(player);
    }

    /**
     * On logout, flush bonded-entity snapshots into player attachments (so live
     * pets are captured into save data before they're discarded) and clear any
     * in-progress hold so the {@link HoldManager} map doesn't carry stale
     * entries for offline players.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        flushLoadedSnapshots(player);
        HoldManager.get().cancel(player);
    }

    /**
     * Server-tick driver for {@link HoldManager}. Mirrors Homebound's
     * {@code WarpManager} pattern — completion fires on the server tick, never
     * on a client-asserted timer. Runs every tick regardless of player count,
     * but {@code tickAll} bails immediately when the active-hold map is empty
     * (the steady state on any server).
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        HoldManager.get().tickAll(event.getServer());
    }

    /**
     * Final flush on server stop: capture live bonded-entity state into player
     * attachments (so it survives the save), then clear every in-memory singleton.
     *
     * <p>The singleton clears matter in single-player, where the integrated server
     * stops but the client JVM keeps running — the static {@code INSTANCE} fields
     * of these singletons would otherwise carry their state into the next world
     * the player loads. On a dedicated server this is a no-op (the next start is
     * a fresh JVM with freshly-initialized singletons), but the cleanup is cheap
     * and the single-player correctness is what we care about.</p>
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            flushLoadedSnapshots(player);
        }
        BondEntityIndex.get().clear();
        HoldManager.get().clear();
        GlobalSummonCooldownTracker.get().clear();
    }

    /**
     * Snapshot every loaded bonded entity belonging to the player into their roster.
     * Used by logout and server-stop to capture state the player attachment would
     * otherwise miss (since the entity hasn't unloaded yet).
     */
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
