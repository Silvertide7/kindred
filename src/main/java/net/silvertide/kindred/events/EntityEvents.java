package net.silvertide.kindred.events;

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
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.attachment.Bond;
import net.silvertide.kindred.attachment.BondRoster;
import net.silvertide.kindred.attachment.Bonded;
import net.silvertide.kindred.bond.BondEntityIndex;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.registry.ModAttachments;
import net.silvertide.kindred.bond.BondService;
import net.silvertide.kindred.data.OfflineSnapshot;
import net.silvertide.kindred.data.KindredSavedData;

import java.util.Optional;

@EventBusSubscriber(modid = Kindred.MODID)
public final class EntityEvents {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (!entity.hasData(ModAttachments.BONDED.get())) return;

        Bonded bonded = entity.getData(ModAttachments.BONDED.get());
        KindredSavedData saved = KindredSavedData.get(level);

        if (saved.isPendingDisband(bonded.bondId())) {
            entity.removeData(ModAttachments.BONDED.get());
            saved.clearPendingDisband(bonded.bondId());
            saved.clearBond(bonded.bondId());
            return;
        }

        int worldRevision = saved.getRevision(bonded.bondId());
        if (bonded.revision() < worldRevision) {
            event.setCanceled(true);
            Kindred.LOGGER.info("[kindred] cancelled stale duplicate of bond {} (entity rev {} < world rev {})",
                    bonded.bondId(), bonded.revision(), worldRevision);
            return;
        }

        BondEntityIndex.get().track(bonded.bondId(), entity);
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (!entity.hasData(ModAttachments.BONDED.get())) return;

        Bonded bonded = entity.getData(ModAttachments.BONDED.get());
        BondEntityIndex.get().untrack(bonded.bondId(), entity);

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
            BondRoster roster = owner.getData(ModAttachments.BOND_ROSTER.get());
            roster.get(bonded.bondId()).ifPresent(b -> {
                Bond updated = b.withSnapshot(deathNbt, dim, pos)
                                .withDiedAt(Optional.of(now));
                owner.setData(ModAttachments.BOND_ROSTER.get(), roster.with(updated));
            });
        } else {
            KindredSavedData.get(level).putOfflineSnapshot(bonded.bondId(),
                    new OfflineSnapshot(deathNbt, dim, pos));
        }
    }

    private static void stripItemsFromSnapshot(CompoundTag nbt) {
        nbt.remove("SaddleItem");       // AbstractHorse saddle — separate top-level compound, NOT in Items
        nbt.remove("Items");            // AbstractChestedHorse chest contents (slots 1+)
        nbt.remove("ArmorItems");       // Mob armor slots (head/chest/legs/feet)
        nbt.remove("HandItems");        // Mob hand slots (mainhand/offhand)
        nbt.remove("body_armor_item");  // 1.21 horse / llama / wolf body armor
        nbt.remove("ChestedHorse");     // donkey/llama chest flag — chest gone, flag should be too
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (Config.DROP_LOOT_ON_DEATH.get()) return;
        if (!event.getEntity().hasData(ModAttachments.BONDED.get())) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (Config.DROP_LOOT_ON_DEATH.get()) return;
        if (!event.getEntity().hasData(ModAttachments.BONDED.get())) return;
        event.setCanceled(true);
    }

    private static void snapshotEntity(ServerLevel level, Entity entity, Bonded bonded) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bonded.ownerUUID());


        if (owner != null) {
            BondRoster preCheck = owner.getData(ModAttachments.BOND_ROSTER.get());
            if (preCheck.get(bonded.bondId()).flatMap(Bond::diedAt).isPresent()) return;
        }

        CompoundTag nbt = entity.saveWithoutId(new CompoundTag());
        ResourceKey<Level> dim = level.dimension();
        Vec3 pos = entity.position();

        if (owner != null) {
            BondRoster roster = owner.getData(ModAttachments.BOND_ROSTER.get());
            Optional<Bond> bond = roster.get(bonded.bondId());
            if (bond.isPresent()) {
                Bond updated = bond.get().withSnapshot(nbt, dim, pos);
                Optional<String> currentName = Optional.ofNullable(entity.getCustomName())
                        .map(Component::getString)
                        .filter(s -> !s.isEmpty());
                if (!currentName.equals(updated.displayName())) {
                    updated = updated.withDisplayName(currentName);
                }
                owner.setData(ModAttachments.BOND_ROSTER.get(), roster.with(updated));
            }
        } else {
            KindredSavedData.get(level).putOfflineSnapshot(bonded.bondId(), new OfflineSnapshot(nbt, dim, pos));
        }
    }

    private EntityEvents() {}
}
