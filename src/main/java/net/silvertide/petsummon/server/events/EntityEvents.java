package net.silvertide.petsummon.server.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.silvertide.petsummon.PetSummon;
import net.silvertide.petsummon.attachment.Bond;
import net.silvertide.petsummon.attachment.BondRoster;
import net.silvertide.petsummon.attachment.Bonded;
import net.silvertide.petsummon.config.Config;
import net.silvertide.petsummon.registry.ModAttachments;
import net.silvertide.petsummon.server.BondIndex;
import net.silvertide.petsummon.server.BondManager;
import net.silvertide.petsummon.server.OfflineSnapshot;
import net.silvertide.petsummon.server.PetSummonSavedData;

import java.util.Optional;

@EventBusSubscriber(modid = PetSummon.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EntityEvents {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (!entity.hasData(ModAttachments.BONDED.get())) return;

        Bonded bonded = entity.getData(ModAttachments.BONDED.get());
        PetSummonSavedData saved = PetSummonSavedData.get(level);

        // Pending disband: bond was broken while this entity was unloaded.
        // Strip its bonded attachment and let it join as a normal entity.
        if (saved.isPendingDisband(bonded.bondId())) {
            entity.removeData(ModAttachments.BONDED.get());
            saved.clearPendingDisband(bonded.bondId());
            saved.clearBond(bonded.bondId());
            return;
        }

        // Anti-dupe: if this entity carries a stale revision, it's a duplicate of one
        // that was re-materialized elsewhere. Cancel the join.
        int worldRevision = saved.getRevision(bonded.bondId());
        if (bonded.revision() < worldRevision) {
            event.setCanceled(true);
            PetSummon.LOGGER.info("[petsummon] cancelled stale duplicate of bond {} (entity rev {} < world rev {})",
                    bonded.bondId(), bonded.revision(), worldRevision);
            return;
        }

        BondIndex.get().track(bonded.bondId(), entity);
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (!entity.hasData(ModAttachments.BONDED.get())) return;

        Bonded bonded = entity.getData(ModAttachments.BONDED.get());
        BondIndex.get().untrack(bonded.bondId(), entity);

        snapshotEntity(level, entity, bonded);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!entity.hasData(ModAttachments.BONDED.get())) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        Bonded bonded = entity.getData(ModAttachments.BONDED.get());

        if (Config.DEATH_IS_PERMANENT.get()) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bonded.ownerUUID());
            if (owner != null) {
                BondManager.breakBond(owner, bonded.bondId());
            } else {
                PetSummonSavedData.get(level).markKilledOffline(bonded.bondId());
            }
            return;
        }

        // Non-permanent death: stamp diedAt for the revival cooldown (only if the
        // owner is online; offline-owner case is a known follow-up). EntityLeaveLevelEvent
        // fires when the corpse is removed and snapshots the dead state; health is
        // restored when the bond is summoned next (BondManager.materializeFresh).
        if (Config.revivalCooldownMs() > 0L) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bonded.ownerUUID());
            if (owner != null) {
                BondRoster roster = owner.getData(ModAttachments.BOND_ROSTER.get());
                roster.get(bonded.bondId()).ifPresent(b -> {
                    Bond updated = b.withDiedAt(Optional.of(System.currentTimeMillis()));
                    owner.setData(ModAttachments.BOND_ROSTER.get(), roster.with(updated));
                });
            }
        }
    }

    /**
     * Suppress vanilla loot/inventory drops for bonded pets when the config opts out.
     * Pairs with the revival cooldown — without this, a horse with a saddle and chest
     * full of gear would scatter all of it on death and the revived pet would come
     * back empty. Cancels the whole drops list (mob loot, equipment, chest contents,
     * wolf armor) since the snapshot taken at corpse-removal still contains them.
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (Config.DROP_LOOT_ON_DEATH.get()) return;
        if (!event.getEntity().hasData(ModAttachments.BONDED.get())) return;
        event.setCanceled(true);
    }

    /**
     * Same opt-out also suppresses XP orbs from bonded-pet deaths. Reviving a pet
     * shouldn't be a renewable XP source either.
     */
    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (Config.DROP_LOOT_ON_DEATH.get()) return;
        if (!event.getEntity().hasData(ModAttachments.BONDED.get())) return;
        event.setCanceled(true);
    }

    private static void snapshotEntity(ServerLevel level, Entity entity, Bonded bonded) {
        CompoundTag nbt = entity.saveWithoutId(new CompoundTag());
        ResourceKey<Level> dim = level.dimension();
        Vec3 pos = entity.position();

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bonded.ownerUUID());
        if (owner != null) {
            BondRoster roster = owner.getData(ModAttachments.BOND_ROSTER.get());
            Optional<Bond> bond = roster.get(bonded.bondId());
            if (bond.isPresent()) {
                Bond updated = bond.get().withSnapshot(nbt, dim, pos);
                // Carry through any customName change made via vanilla nametag while
                // the pet was loaded — keeps the roster in sync with what's in-world.
                Optional<String> currentName = Optional.ofNullable(entity.getCustomName())
                        .map(Component::getString)
                        .filter(s -> !s.isEmpty());
                if (!currentName.equals(updated.displayName())) {
                    updated = updated.withDisplayName(currentName);
                }
                owner.setData(ModAttachments.BOND_ROSTER.get(), roster.with(updated));
            }
        } else {
            PetSummonSavedData.get(level).putOfflineSnapshot(bonded.bondId(), new OfflineSnapshot(nbt, dim, pos));
        }
    }

    private EntityEvents() {}
}
