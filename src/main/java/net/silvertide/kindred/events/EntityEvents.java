package net.silvertide.kindred.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.attachment.Bond;
import net.silvertide.kindred.attachment.BondRoster;
import net.silvertide.kindred.attachment.Bonded;
import net.silvertide.kindred.bond.BondEntityIndex;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.attachment.KindredData;
import net.silvertide.kindred.bond.BondService;
import net.silvertide.kindred.data.OfflineSnapshot;
import net.silvertide.kindred.data.KindredSavedData;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = Kindred.MODID)
public final class EntityEvents {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (!KindredData.isBonded(entity)) return;

        Bonded bonded = KindredData.getBonded(entity).orElseThrow();
        KindredSavedData saved = KindredSavedData.get(level);

        int worldRevision = saved.getRevision(bonded.bondId());
        if (worldRevision == 0) {
            KindredData.removeBonded(entity);
            saved.clearBond(bonded.bondId());
            Kindred.LOGGER.info("[kindred] released orphaned copy of former bond {} (no revision on record)",
                    bonded.bondId());
            return;
        }

        if (bonded.revision() < worldRevision) {
            event.setCanceled(true);
            Kindred.LOGGER.info("[kindred] cancelled stale duplicate of bond {} (entity rev {} < world rev {})",
                    bonded.bondId(), bonded.revision(), worldRevision);
            return;
        }

        if (saved.isPendingDisband(bonded.bondId())) {
            KindredData.removeBonded(entity);
            saved.clearPendingDisband(bonded.bondId());
            saved.clearBond(bonded.bondId());
            return;
        }

        BondEntityIndex.get().track(bonded.bondId(), entity);
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (!KindredData.isBonded(entity)) return;

        Bonded bonded = KindredData.getBonded(entity).orElseThrow();
        BondEntityIndex.get().untrack(bonded.bondId(), entity);

        snapshotEntity(level, entity, bonded);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!KindredData.isBonded(entity)) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        Bonded bonded = KindredData.getBonded(entity).orElseThrow();

        if (Config.DEATH_IS_PERMANENT.get()) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bonded.ownerUUID());
            if (owner != null) {
                BondService.breakBond(owner, bonded.bondId());
            } else {
                KindredSavedData.get(level).markKilledOffline(bonded.bondId());
            }
            return;
        }

        CompoundTag deathNbt = entity.saveWithoutId(new CompoundTag());
        if (Config.DROP_LOOT_ON_DEATH.get()) {
            stripItemsFromSnapshot(deathNbt);
        }

        long now = System.currentTimeMillis();
        ResourceKey<Level> dim = level.dimension();
        Vec3 pos = entity.position();

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bonded.ownerUUID());
        if (owner != null) {
            BondRoster roster = KindredData.getRoster(owner);
            roster.get(bonded.bondId()).ifPresent(b -> {
                Bond updated = b.withSnapshot(deathNbt, dim, pos)
                                .withDiedAt(Optional.of(now));
                KindredData.setRoster(owner, roster.with(updated));
            });
        } else {
            KindredSavedData saved = KindredSavedData.get(level);
            saved.putOfflineSnapshot(bonded.bondId(), new OfflineSnapshot(deathNbt, dim, pos));
            saved.markDiedOffline(bonded.bondId(), now);
        }
    }

    private static void stripItemsFromSnapshot(CompoundTag nbt) {
        nbt.remove("SaddleItem");       // AbstractHorse saddle — separate top-level compound, NOT in Items
        nbt.remove("Items");            // AbstractChestedHorse chest contents (slots 1+)
        nbt.remove("ArmorItems");       // Mob armor slots (head/chest/legs/feet)
        nbt.remove("HandItems");        // Mob hand slots (mainhand/offhand)
        nbt.remove("ArmorItem");        // Horse armor — separate top-level compound in 1.20.1
        nbt.remove("DecorItem");        // Llama carpet — separate top-level compound in 1.20.1
        nbt.remove("ChestedHorse");     // donkey/llama chest flag — chest gone, flag should be too
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (Config.DROP_LOOT_ON_DEATH.get()) return;
        if (!KindredData.isBonded(event.getEntity())) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (Config.DROP_LOOT_ON_DEATH.get()) return;
        if (!KindredData.isBonded(event.getEntity())) return;
        event.setCanceled(true);
    }

    private static void snapshotEntity(ServerLevel level, Entity entity, Bonded bonded) {
        if (entity instanceof LivingEntity living && living.isDeadOrDying()) return;

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bonded.ownerUUID());

        if (owner != null) {
            BondRoster preCheck = KindredData.getRoster(owner);
            if (preCheck.get(bonded.bondId()).flatMap(Bond::diedAt).isPresent()) return;
        }

        CompoundTag nbt = entity.saveWithoutId(new CompoundTag());
        ResourceKey<Level> dim = level.dimension();
        Vec3 pos = entity.position();

        if (owner != null) {
            BondRoster roster = KindredData.getRoster(owner);
            Optional<Bond> bond = roster.get(bonded.bondId());
            if (bond.isPresent()) {
                Bond updated = bond.get().withSnapshot(nbt, dim, pos);
                Optional<String> currentName = Optional.ofNullable(entity.getCustomName())
                        .map(Component::getString)
                        .filter(s -> !s.isEmpty());
                if (!currentName.equals(updated.displayName())) {
                    updated = updated.withDisplayName(currentName);
                }
                KindredData.setRoster(owner, roster.with(updated));
            }
        } else {
            KindredSavedData.get(level).putOfflineSnapshot(bonded.bondId(), new OfflineSnapshot(nbt, dim, pos));
        }
    }

    private EntityEvents() {}
}
