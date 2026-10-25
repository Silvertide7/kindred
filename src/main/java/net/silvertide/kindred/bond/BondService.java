package net.silvertide.kindred.bond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ITeleporter;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.bond.bond_results.BreakResult;
import net.silvertide.kindred.bond.bond_results.ClaimResult;
import net.silvertide.kindred.bond.bond_results.DismissResult;
import net.silvertide.kindred.bond.bond_results.SummonResult;
import net.silvertide.kindred.events.BondClaimEvent;
import net.silvertide.kindred.attachment.Bond;
import net.silvertide.kindred.attachment.BondRoster;
import net.silvertide.kindred.attachment.Bonded;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.data.KindredSavedData;
import net.silvertide.kindred.attachment.KindredData;
import net.silvertide.kindred.registry.ModAttributes;
import net.silvertide.kindred.registry.ModTags;

import java.util.Optional;
import java.util.UUID;

public final class BondService {

    private static final double ATTRIBUTE_DRIFT_EPSILON = 1.0E-6;

    public static int effectiveMaxBonds(Player player) {
        return (int) Math.floor(player.getAttributeValue(ModAttributes.MAX_COMPANION_BONDS.get()) + ATTRIBUTE_DRIFT_EPSILON);
    }

    public static ClaimResult checkClaimEligibility(ServerPlayer player, Entity target) {
        if (!CompanionAdapters.isOwnable(target)) return ClaimResult.NOT_OWNABLE;
        if (!CompanionAdapters.ownerOf(target).map(player.getUUID()::equals).orElse(false)) return ClaimResult.NOT_OWNED_BY_PLAYER;

        var entityTypeTags = ForgeRegistries.ENTITY_TYPES.tags();
        boolean allowlistActive = !entityTypeTags.getTag(ModTags.BOND_ALLOWLIST).isEmpty();
        if (allowlistActive) {
            if (!entityTypeTags.getTag(ModTags.BOND_ALLOWLIST).contains(target.getType())) return ClaimResult.NOT_ALLOWED;
        } else if (entityTypeTags.getTag(ModTags.BOND_DENYLIST).contains(target.getType())) {
            return ClaimResult.NOT_ALLOWED;
        }
        if (Config.REQUIRE_SADDLEABLE.get() && !(target instanceof Saddleable)) return ClaimResult.REQUIRES_SADDLEABLE;
        BondRoster roster = KindredData.getRoster(player);

        int effectiveCap = effectiveMaxBonds(player);
        if (effectiveCap <= 0) return ClaimResult.BONDING_LOCKED;
        if (roster.size() >= effectiveCap) return ClaimResult.AT_CAPACITY;
        if (KindredData.isBonded(target)) return ClaimResult.ALREADY_BONDED;

        int xpCost = Config.BOND_XP_LEVEL_COST.get();
        if (xpCost > 0 && !player.isCreative() && player.experienceLevel < xpCost) {
            return ClaimResult.NOT_ENOUGH_XP;
        }

        return ClaimResult.CLAIMED;
    }

    public static ClaimResult tryClaim(ServerPlayer player, Entity target) {
        ClaimResult eligibility = checkClaimEligibility(player, target);
        if (eligibility != ClaimResult.CLAIMED) return eligibility;

        BondClaimEvent event = new BondClaimEvent(player, target);
        if (MinecraftForge.EVENT_BUS.post(event)) return ClaimResult.CANCELLED;

        BondRoster roster = KindredData.getRoster(player);
        ServerLevel level = (ServerLevel) target.level();
        KindredSavedData saved = KindredSavedData.get(level);

        UUID bondId = UUID.randomUUID();
        int revision = saved.incrementRevision(bondId);

        CompoundTag snapshot = target.saveWithoutId(new CompoundTag());
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        long now = System.currentTimeMillis();

        Optional<String> initialName = Optional.ofNullable(target.getCustomName())
                .map(Component::getString)
                .filter(s -> !s.isEmpty());

        Bond bond = new Bond(
                bondId,
                typeId,
                snapshot,
                level.dimension(),
                target.position(),
                revision,
                initialName,
                now,
                0L,
                Optional.empty(),
                false,
                true
        );

        BondRoster newRoster = roster.with(bond);
        if (newRoster.activePetId().isEmpty()) {
            newRoster = newRoster.withActive(Optional.of(bondId));
        }
        KindredData.setRoster(player, newRoster);
        KindredData.setBonded(target, new Bonded(bondId, player.getUUID(), revision));
        applyNameVisibility(target);
        BondEntityIndex.get().track(bondId, target);

        int xpCost = Config.BOND_XP_LEVEL_COST.get();
        if (xpCost > 0 && !player.isCreative()) {
            player.giveExperienceLevels(-xpCost);
        }

        Kindred.LOGGER.info("[kindred] {} claimed bond {} on {}", player.getGameProfile().getName(), bondId, typeId);
        return ClaimResult.CLAIMED;
    }

    public static BreakResult breakBond(ServerPlayer player, UUID bondId) {
        BondRoster roster = KindredData.getRoster(player);
        Optional<Bond> maybeBond = roster.get(bondId);
        if (maybeBond.isEmpty()) return BreakResult.NO_SUCH_BOND;
        if (isRevivalPending(maybeBond.get())) return BreakResult.REVIVAL_PENDING;

        ServerLevel level = (ServerLevel) player.level();
        KindredSavedData saved = KindredSavedData.get(level);

        Optional<Entity> existing = BondEntityIndex.get().find(bondId);
        Bond bond = maybeBond.get();
        if (existing.isEmpty() && (bond.dismissed() || bond.diedAt().isPresent())) {
            SummonResult release = releaseFromSnapshot(player, bond, level, saved);
            if (!release.isSuccess()) {
                Kindred.LOGGER.warn("[kindred] break of bond {} aborted: could not release the pet ({})",
                        bondId, release);
                return BreakResult.RELEASE_FAILED;
            }
            existing = BondEntityIndex.get().find(bondId);
        }

        BondRoster current = KindredData.getRoster(player);
        KindredData.setRoster(player, current.without(bondId));

        if (existing.isPresent()) {
            Entity entity = existing.get();
            KindredData.removeBonded(entity);
            BondEntityIndex.get().untrack(bondId);
            entity.setCustomNameVisible(false);
            saved.clearBond(bondId);
        } else {
            saved.markPendingDisband(bondId);
        }

        Kindred.LOGGER.info("[kindred] {} broke bond {}", player.getGameProfile().getName(), bondId);
        return BreakResult.BROKEN;
    }

    public static DismissResult dismiss(ServerPlayer player, UUID bondId) {
        if (!Config.ALLOW_DISMISSING.get()) return DismissResult.DISABLED;
        BondRoster roster = KindredData.getRoster(player);
        Optional<Bond> bondOpt = roster.get(bondId);
        if (bondOpt.isEmpty()) return DismissResult.NO_SUCH_BOND;

        Optional<Entity> existing = BondEntityIndex.get().find(bondId);
        if (existing.isEmpty()) return DismissResult.NOT_LOADED;
        Entity entity = existing.get();

        KindredData.setRoster(player, roster.with(bondOpt.get().withDismissed(true)));

        entity.ejectPassengers();
        if (entity.isPassenger()) entity.stopRiding();

        ServerLevel entityLevel = (ServerLevel) entity.level();
        double cx = entity.getX();
        double cy = entity.getY() + entity.getBbHeight() / 2.0D;
        double cz = entity.getZ();

        entityLevel.sendParticles(ParticleTypes.POOF, cx, cy, cz,
                20, 0.3D, 0.3D, 0.3D, 0.05D);
        entityLevel.playSound(null, cx, cy, cz,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.3F, 1.2F);

        entity.discard();

        Kindred.LOGGER.info("[kindred] {} dismissed bond {}", player.getGameProfile().getName(), bondId);
        return DismissResult.DISMISSED;
    }

    public static Optional<SummonResult> checkSummonGate(ServerPlayer player, UUID bondId) {
        BondRoster roster = KindredData.getRoster(player);
        Optional<Bond> maybeBond = roster.get(bondId);
        if (maybeBond.isEmpty()) return Optional.of(SummonResult.NO_SUCH_BOND);
        Bond bond = maybeBond.get();

        if (isRevivalPending(bond)) return Optional.of(SummonResult.REVIVAL_PENDING);

        long nowMs = System.currentTimeMillis();
        if (nowMs - bond.lastSummonedAt() < Config.summonCooldownMs()) {
            return Optional.of(SummonResult.ON_COOLDOWN);
        }

        long globalCooldownMs = Config.summonGlobalCooldownMs();
        if (GlobalSummonCooldownTracker.get().remainingMs(player.getUUID(), globalCooldownMs) > 0L) {
            return Optional.of(SummonResult.GLOBAL_COOLDOWN);
        }

        return Optional.empty();
    }

    public static boolean isRevivalPending(Bond bond) {
        if (bond.diedAt().isEmpty()) return false;
        long revivalCooldownMs = Config.revivalCooldownMs();
        if (revivalCooldownMs <= 0L) return false;
        return System.currentTimeMillis() - bond.diedAt().get() < revivalCooldownMs;
    }

    public static SummonResult summon(ServerPlayer player, UUID bondId) {
        Optional<SummonResult> gateFailure = checkSummonGate(player, bondId);
        if (gateFailure.isPresent()) return gateFailure.get();

        Bond bond = KindredData.getRoster(player).get(bondId).orElseThrow();

        if (Config.REQUIRE_SPACE.get() && !BondSpawnLocator.isPlayerGrounded(player)) return SummonResult.PLAYER_AIRBORNE;

        ServerLevel playerLevel = (ServerLevel) player.level();
        KindredSavedData saved = KindredSavedData.get(playerLevel);
        ResourceKey<Level> playerDim = playerLevel.dimension();

        if (playerLevel.dimensionTypeRegistration().is(ModTags.NO_SUMMON_DIMENSIONS)) {
            return SummonResult.BANNED_DIMENSION;
        }
        if (playerLevel.getBiome(player.blockPosition()).is(ModTags.NO_SUMMON_BIOMES)) {
            return SummonResult.BANNED_BIOME;
        }

        Optional<Entity> existing = BondEntityIndex.get().find(bondId);
        if (existing.isPresent() && isDying(existing.get())) return SummonResult.REVIVAL_PENDING;
        if (existing.isPresent()) {
            Entity old = existing.get();
            ResourceKey<Level> oldDim = old.level().dimension();
            boolean autoMount = shouldAutoMount(player, bond, old);

            if (oldDim.equals(playerDim)) {
                double distSq = old.distanceToSqr(player);
                double walkRange = Config.WALK_RANGE.get();

                if (!autoMount && distSq <= walkRange * walkRange && old instanceof Mob mob) {
                    CompanionAdapters.wake(old);
                    freshenForSummon(mob);
                    mob.getNavigation().moveTo(player.getX(), player.getY(), player.getZ(), Config.WALK_SPEED.get());
                    playSummonFx(playerLevel, player.getX(), player.getY(), player.getZ(), false);
                    writeSummonTimestamp(player, bond);
                    GlobalSummonCooldownTracker.get().recordSummon(player.getUUID());
                    return SummonResult.WALKING;
                }
                return teleportSameDim(player, old, bond, playerLevel, saved, autoMount);
            }
            if (!Config.CROSS_DIM_ALLOWED.get()) return SummonResult.CROSS_DIM_BLOCKED;
            return teleportLoaded(player, old, bond, playerLevel, saved, autoMount);
        }

        return summonFromSnapshot(player, bond, playerLevel, saved);
    }

    private static boolean shouldAutoMount(ServerPlayer player, Bond bond, Entity pet) {
        return Config.ALLOW_AUTO_MOUNT.get()
                && bond.autoMount()
                && isPlayerReadyToMount(player)
                && isPetReadyToBeRidden(pet)
                && hasSafeRoomForMountAndRider(player, pet);
    }

    private static boolean isPlayerReadyToMount(ServerPlayer player) {
        return !player.isPassenger()
                && !player.isShiftKeyDown()
                && !player.isInWater()
                && player.onGround();
    }

    private static boolean isPetReadyToBeRidden(Entity pet) {
        if (!Mountability.isMountable(pet) || pet.isVehicle()) return false;
        if (pet instanceof LivingEntity living && living.isBaby()) return false;
        if (pet instanceof AbstractHorse horse && !horse.isTamed()) return false;
        if (pet instanceof Saddleable saddleable && saddleable.isSaddleable()) return saddleable.isSaddled();
        return true;
    }

    private static boolean hasSafeRoomForMountAndRider(ServerPlayer player, Entity pet) {
        AABB petBox = pet.getDimensions(Pose.STANDING).makeBoundingBox(player.position());
        AABB petAndRiderBox = petBox.expandTowards(0.0D, player.getDimensions(Pose.STANDING).height, 0.0D);
        ServerLevel level = player.serverLevel();
        return level.noCollision(petAndRiderBox) && !BondSpawnLocator.hasLavaInOrNear(level, petBox);
    }

    private static void mountPetOrPlaceNearby(ServerPlayer player, Entity pet) {
        if (mountPet(player, pet)) return;
        BondSpawnLocator.findSpawnLocation(player.serverLevel(), player, pet.getType().getDimensions())
                .ifPresent(nearby -> pet.teleportTo(nearby.x, nearby.y, nearby.z));
    }

    private static boolean mountPet(ServerPlayer player, Entity pet) {
        if (pet instanceof AbstractHorse horse) {
            horse.setEating(false);
            horse.setStanding(false);
        }
        return player.startRiding(pet);
    }

    private static Optional<Vec3> findSummonPosition(ServerLevel level, ServerPlayer player, EntityType<?> petType, boolean autoMount) {
        if (autoMount) return Optional.of(player.position());
        Optional<Vec3> found = BondSpawnLocator.findSpawnLocation(level, player, petType.getDimensions());
        if (found.isPresent() || Config.REQUIRE_SPACE.get()) return found;
        return Optional.of(player.position());
    }

    private static SummonResult teleportSameDim(ServerPlayer player, Entity old, Bond bond, ServerLevel level, KindredSavedData saved, boolean autoMount) {
        UUID bondId = bond.bondId();

        Optional<Vec3> found = findSummonPosition(level, player, old.getType(), autoMount);
        if (found.isEmpty()) return SummonResult.NO_SPACE;
        Vec3 spawnPos = found.get();

        CompanionAdapters.wake(old);
        if (old instanceof LivingEntity oldLiving) freshenForSummon(oldLiving);
        old.stopRiding();
        old.setYRot(player.getYRot());
        old.teleportTo(spawnPos.x, spawnPos.y, spawnPos.z);
        if (autoMount) mountPetOrPlaceNearby(player, old);

        // This probably isn't necessary, but in the case that a chunk crashes while the tp is happening this will guarantee de-duping the entity.
        int newRevision = saved.incrementRevision(bondId);
        KindredData.setBonded(old, new Bonded(bondId, player.getUUID(), newRevision));

        playSummonFx(level, spawnPos.x, spawnPos.y, spawnPos.z, true);

        BondRoster currentRoster = KindredData.getRoster(player);
        Optional<Bond> currentBond = currentRoster.get(bondId);
        if (currentBond.isPresent()) {
            Bond updated = currentBond.get()
                    .withRevision(newRevision)
                    .withLastSummonedAt(System.currentTimeMillis());
            KindredData.setRoster(player, currentRoster.with(updated));
        }

        GlobalSummonCooldownTracker.get().recordSummon(player.getUUID());
        return SummonResult.TELEPORTED_NEAR;
    }

    private static SummonResult teleportLoaded(ServerPlayer player, Entity old, Bond bond, ServerLevel targetLevel, KindredSavedData saved, boolean autoMount) {
        UUID bondId = bond.bondId();

        Optional<Vec3> found = findSummonPosition(targetLevel, player, old.getType(), autoMount);
        if (found.isEmpty()) return SummonResult.NO_SPACE;
        Vec3 spawnPos = found.get();

        CompanionAdapters.wake(old);

        Vec3 destination = spawnPos;
        ITeleporter teleporter = new ITeleporter() {
            @Override
            public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld, java.util.function.Function<ServerLevel, PortalInfo> defaultPortalInfo) {
                return new PortalInfo(destination, Vec3.ZERO, player.getYRot(), entity.getXRot());
            }

            @Override
            public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, java.util.function.Function<Boolean, Entity> repositionEntity) {
                return repositionEntity.apply(false);
            }
        };

        Entity teleported;
        try {
            teleported = old.changeDimension(targetLevel, teleporter);
        } catch (Throwable t) {
            Kindred.LOGGER.warn("[kindred] SPAWN_FAILED: changeDimension threw for {} (bond {} for {})",
                    bond.entityType(), bondId, player.getGameProfile().getName(), t);
            return SummonResult.SPAWN_FAILED;
        }
        if (teleported == null) {
            Kindred.LOGGER.warn("[kindred] SPAWN_FAILED: changeDimension returned null for {} (bond {} for {})",
                    bond.entityType(), bondId, player.getGameProfile().getName());
            return SummonResult.SPAWN_FAILED;
        }

        applyDisplayName(teleported, bond.displayName());
        if (teleported instanceof LivingEntity teleportedLiving) freshenForSummon(teleportedLiving);
        if (autoMount) mountPetOrPlaceNearby(player, teleported);

        int newRevision = saved.incrementRevision(bondId);
        KindredData.setBonded(teleported, new Bonded(bondId, player.getUUID(), newRevision));

        playSummonFx(targetLevel, spawnPos.x, spawnPos.y, spawnPos.z, true);

        BondRoster currentRoster = KindredData.getRoster(player);
        Optional<Bond> currentBond = currentRoster.get(bondId);
        if (currentBond.isPresent()) {
            CompoundTag freshNbt = teleported.saveWithoutId(new CompoundTag());
            Bond updated = currentBond.get()
                    .withSnapshot(freshNbt, targetLevel.dimension(), spawnPos)
                    .withRevision(newRevision)
                    .withLastSummonedAt(System.currentTimeMillis())
                    .withDiedAt(Optional.empty())
                    .withDismissed(false);
            KindredData.setRoster(player, currentRoster.with(updated));
        }

        GlobalSummonCooldownTracker.get().recordSummon(player.getUUID());

        return SummonResult.SUMMONED_FRESH;
    }

    private static SummonResult summonFromSnapshot(ServerPlayer player, Bond bond, ServerLevel targetLevel, KindredSavedData saved) {
        return materializeFromSnapshot(player, bond, targetLevel, saved, true);
    }

    private static SummonResult releaseFromSnapshot(ServerPlayer player, Bond bond, ServerLevel targetLevel, KindredSavedData saved) {
        return materializeFromSnapshot(player, bond, targetLevel, saved, false);
    }

    private static SummonResult materializeFromSnapshot(ServerPlayer player, Bond bond, ServerLevel targetLevel, KindredSavedData saved, boolean mountIfEligible) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(bond.entityType());
        if (type == null) {
            Kindred.LOGGER.warn("[kindred] SPAWN_FAILED: entity type {} not in registry (bond {} for {})",
                    bond.entityType(), bond.bondId(), player.getGameProfile().getName());
            return SummonResult.SPAWN_FAILED;
        }

        Entity entity = type.create(targetLevel);
        if (entity == null) {
            Kindred.LOGGER.warn("[kindred] SPAWN_FAILED: {}.create() returned null (bond {} for {} in {})",
                    bond.entityType(), bond.bondId(), player.getGameProfile().getName(), targetLevel.dimension().location());
            return SummonResult.SPAWN_FAILED;
        }

        entity.load(bond.nbtSnapshot());

        applyDisplayName(entity, bond.displayName());

        if (entity instanceof LivingEntity living) {
            if (!Config.DEATH_IS_PERMANENT.get() && living.getHealth() <= 0) {
                freshenForRevival(living);
            } else {
                freshenForSummon(living);
            }
        }

        CompanionAdapters.wake(entity);

        boolean autoMount = mountIfEligible && shouldAutoMount(player, bond, entity);
        Optional<Vec3> found = findSummonPosition(targetLevel, player, type, autoMount);
        if (found.isEmpty()) return SummonResult.NO_SPACE;
        Vec3 spawnPos = found.get();

        entity.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

        int newRevision = saved.incrementRevision(bond.bondId());
        KindredData.setBonded(entity, new Bonded(bond.bondId(), player.getUUID(), newRevision));

        if (!targetLevel.addFreshEntity(entity)) {
            BondEntityIndex.get().untrack(bond.bondId(), entity);
            Kindred.LOGGER.warn("[kindred] SPAWN_FAILED: addFreshEntity rejected {} at {} in {} (bond {} for {})",
                    bond.entityType(), spawnPos, targetLevel.dimension().location(),
                    bond.bondId(), player.getGameProfile().getName());
            return SummonResult.SPAWN_FAILED;
        }
        if (autoMount) mountPetOrPlaceNearby(player, entity);

        playSummonFx(targetLevel, spawnPos.x, spawnPos.y, spawnPos.z, true);

        BondRoster currentRoster = KindredData.getRoster(player);
        Bond updated = bond.withRevision(newRevision)
                .withLastSummonedAt(System.currentTimeMillis())
                .withDiedAt(Optional.empty())  // successful materialize IS the revival
                .withDismissed(false);         // entity is back in the world
        KindredData.setRoster(player, currentRoster.with(updated));

        GlobalSummonCooldownTracker.get().recordSummon(player.getUUID());

        return SummonResult.SUMMONED_FRESH;
    }

    public static void applyNameVisibility(Entity entity) {
        if (entity.hasCustomName()) entity.setCustomNameVisible(Config.ALWAYS_SHOW_PET_NAMES.get());
    }

    public static void applyDisplayName(Entity entity, Optional<String> displayName) {
        if (displayName.isPresent()) {
            entity.setCustomName(Component.literal(displayName.get()));
            entity.setCustomNameVisible(Config.ALWAYS_SHOW_PET_NAMES.get());
        } else {
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
        }
    }

    private static boolean isDying(Entity entity) {
        return entity instanceof LivingEntity living && living.isDeadOrDying();
    }

    private static void freshenForSummon(LivingEntity living) {
        living.clearFire();
        living.setTicksFrozen(0);
        living.fallDistance = 0F;
        living.setAirSupply(living.getMaxAirSupply());
    }

    private static void freshenForRevival(LivingEntity living) {
        freshenForSummon(living);
        living.setHealth(living.getMaxHealth());
        living.removeAllEffects();
    }

    private static void playSummonFx(ServerLevel level, double x, double y, double z, boolean withParticles) {
        if (withParticles) {
            level.sendParticles(ParticleTypes.POOF, x, y + 0.5D, z, 20, 0.3D, 0.3D, 0.3D, 0.05D);
        }
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.5F, 1.0F);
    }

    private static void writeSummonTimestamp(ServerPlayer player, Bond bond) {
        // Re-read the roster fresh — we don't hold a reference across the call
        // chain that got us here, and the player's roster may have been touched
        // by a sibling packet handler on the same server tick.
        // Successful summon also clears any pending revival cooldown — this IS the revival.
        BondRoster roster = KindredData.getRoster(player);
        Bond updated = bond.withLastSummonedAt(System.currentTimeMillis()).withDiedAt(Optional.empty());
        KindredData.setRoster(player, roster.with(updated));
    }

    private BondService() {}
}
