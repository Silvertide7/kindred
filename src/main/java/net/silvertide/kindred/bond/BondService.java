package net.silvertide.kindred.bond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.bond.bond_results.BreakResult;
import net.silvertide.kindred.bond.bond_results.ClaimResult;
import net.silvertide.kindred.bond.bond_results.DismissResult;
import net.silvertide.kindred.bond.bond_results.SummonResult;
import net.silvertide.kindred.events.BondClaimEvent;
import net.silvertide.kindred.attachment.Bond;
import net.silvertide.kindred.attachment.BondRoster;
import net.silvertide.kindred.attachment.Bonded;
import net.silvertide.kindred.compat.pmmo.PmmoCompat;
import net.silvertide.kindred.compat.pmmo.PmmoMode;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.data.KindredSavedData;
import net.silvertide.kindred.registry.ModAttachments;
import net.silvertide.kindred.registry.ModTags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BondService {

    public static int effectiveMaxBonds(Player player) {
        int hardCap = Config.MAX_BONDS.get();
        if (!Config.PMMO_ENABLED.get() || !PmmoCompat.isAvailable()) return hardCap;
        long level = PmmoCompat.getSkillLevel(player, Config.PMMO_SKILL.get());
        int startLevel = Config.PMMO_START_LEVEL.get();
        if (level < startLevel) return 0;
        if (Config.PMMO_MODE.get() == PmmoMode.ALL_OR_NOTHING) return hardCap;
        int increment = Math.max(1, Config.PMMO_INCREMENT_PER_BOND.get());
        long allowed = ((level - startLevel) / increment) + 1;
        return (int) Math.min(hardCap, allowed);
    }

    public static ClaimResult checkClaimEligibility(ServerPlayer player, Entity target) {
        if (!(target instanceof OwnableEntity owned)) return ClaimResult.NOT_OWNABLE;
        if (!player.getUUID().equals(owned.getOwnerUUID())) return ClaimResult.NOT_OWNED_BY_PLAYER;

        var typeHolder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType());
        boolean allowlistActive = BuiltInRegistries.ENTITY_TYPE.getTag(ModTags.BOND_ALLOWLIST)
                .map(t -> t.size() > 0).orElse(false);
        if (allowlistActive) {
            if (!typeHolder.is(ModTags.BOND_ALLOWLIST)) return ClaimResult.NOT_ALLOWED;
        } else if (typeHolder.is(ModTags.BOND_DENYLIST)) {
            return ClaimResult.NOT_ALLOWED;
        }
        if (Config.REQUIRE_SADDLEABLE.get() && !(target instanceof Saddleable)) return ClaimResult.REQUIRES_SADDLEABLE;
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());

        // Pmmo Check
        int effectiveCap = effectiveMaxBonds(player);
        if (effectiveCap == 0) return ClaimResult.PMMO_LOCKED;
        if (roster.size() >= effectiveCap) return ClaimResult.AT_CAPACITY;
        if (target.hasData(ModAttachments.BONDED.get())) return ClaimResult.ALREADY_BONDED;

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
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) return ClaimResult.CANCELLED;

        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        ServerLevel level = (ServerLevel) target.level();
        KindredSavedData saved = KindredSavedData.get(level);

        UUID bondId = UUID.randomUUID();
        int revision = saved.incrementRevision(bondId);

        CompoundTag snapshot = target.saveWithoutId(new CompoundTag());
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
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
                false
        );

        BondRoster newRoster = roster.with(bond);
        if (newRoster.activePetId().isEmpty()) {
            newRoster = newRoster.withActive(Optional.of(bondId));
        }
        player.setData(ModAttachments.BOND_ROSTER.get(), newRoster);
        target.setData(ModAttachments.BONDED.get(), new Bonded(bondId, player.getUUID(), revision));
        BondEntityIndex.get().track(bondId, target);

        int xpCost = Config.BOND_XP_LEVEL_COST.get();
        if (xpCost > 0 && !player.isCreative()) {
            player.giveExperienceLevels(-xpCost);
        }

        Kindred.LOGGER.info("[kindred] {} claimed bond {} on {}", player.getGameProfile().getName(), bondId, typeId);
        return ClaimResult.CLAIMED;
    }

    public static BreakResult breakBond(ServerPlayer player, UUID bondId) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        Optional<Bond> maybeBond = roster.get(bondId);
        if (maybeBond.isEmpty()) return BreakResult.NO_SUCH_BOND;

        ServerLevel level = (ServerLevel) player.level();
        KindredSavedData saved = KindredSavedData.get(level);

        Optional<Entity> existing = BondEntityIndex.get().find(bondId);
        Bond bond = maybeBond.get();
        if (existing.isEmpty() && (bond.dismissed() || bond.diedAt().isPresent())) {
            materializeFresh(player, bond, level, saved);
            existing = BondEntityIndex.get().find(bondId);
        }

        BondRoster current = player.getData(ModAttachments.BOND_ROSTER.get());
        player.setData(ModAttachments.BOND_ROSTER.get(), current.without(bondId));

        if (existing.isPresent()) {
            Entity entity = existing.get();
            entity.removeData(ModAttachments.BONDED.get());
            BondEntityIndex.get().untrack(bondId);
            saved.clearBond(bondId);
        } else {
            saved.clearBond(bondId);
            saved.markPendingDisband(bondId);
        }

        Kindred.LOGGER.info("[kindred] {} broke bond {}", player.getGameProfile().getName(), bondId);
        return BreakResult.BROKEN;
    }

    public static DismissResult dismiss(ServerPlayer player, UUID bondId) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        Optional<Bond> bondOpt = roster.get(bondId);
        if (bondOpt.isEmpty()) return DismissResult.NO_SUCH_BOND;

        Optional<Entity> existing = BondEntityIndex.get().find(bondId);
        if (existing.isEmpty()) return DismissResult.NOT_LOADED;
        Entity entity = existing.get();

        player.setData(ModAttachments.BOND_ROSTER.get(),
                roster.with(bondOpt.get().withDismissed(true)));

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
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
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

    /**
     * True if the bond is in its post-death revival cooldown window. Returns
     * false when there's no death timestamp or when revival cooldowns are
     * disabled by config. Shared between {@link #checkSummonGate} and the
     * dismiss/break eligibility checks in {@code HoldEligibility}.
     */
    public static boolean isRevivalPending(Bond bond) {
        if (bond.diedAt().isEmpty()) return false;
        long revivalCooldownMs = Config.revivalCooldownMs();
        if (revivalCooldownMs <= 0L) return false;
        return System.currentTimeMillis() - bond.diedAt().get() < revivalCooldownMs;
    }

    public static SummonResult summon(ServerPlayer player, UUID bondId) {
        Optional<SummonResult> gateFailure = checkSummonGate(player, bondId);
        if (gateFailure.isPresent()) return gateFailure.get();

        // checkSummonGate guarantees the bond exists, so this is safe.
        Bond bond = player.getData(ModAttachments.BOND_ROSTER.get()).get(bondId).orElseThrow();

        if (Config.REQUIRE_SPACE.get() && !isPlayerGrounded(player)) return SummonResult.PLAYER_AIRBORNE;

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
        if (existing.isPresent()) {
            Entity old = existing.get();
            ResourceKey<Level> oldDim = old.level().dimension();

            if (oldDim.equals(playerDim)) {
                double dx = old.getX() - player.getX();
                double dz = old.getZ() - player.getZ();
                double distSq = dx * dx + dz * dz;
                double walkRange = Config.WALK_RANGE.get();

                if (distSq <= walkRange * walkRange && old instanceof Mob mob) {
                    wake(old);
                    freshenForSummon(mob);
                    mob.getNavigation().moveTo(player.getX(), player.getY(), player.getZ(), Config.WALK_SPEED.get());
                    playSummonFx(playerLevel, player.getX(), player.getY(), player.getZ(), false);
                    writeSummonTimestamp(player, bond);
                    GlobalSummonCooldownTracker.get().recordSummon(player.getUUID());
                    return SummonResult.WALKING;
                }
                return teleportSameDim(player, old, bond, playerLevel, saved);
            }
            if (!Config.CROSS_DIM_ALLOWED.get()) return SummonResult.CROSS_DIM_BLOCKED;
            return teleportLoaded(player, old, bond, playerLevel, saved, SummonResult.SUMMONED_FRESH);
        }

        // Not loaded anywhere — materialize from stored snapshot
        return materializeFresh(player, bond, playerLevel, saved);
    }

    private static SummonResult teleportSameDim(ServerPlayer player, Entity old, Bond bond, ServerLevel level, KindredSavedData saved) {
        UUID bondId = bond.bondId();

        Optional<Vec3> found = findSpawnLocation(level, player, old.getType().getDimensions());
        if (found.isEmpty() && Config.REQUIRE_SPACE.get()) return SummonResult.NO_SPACE;
        Vec3 spawnPos = found.orElse(player.position());

        wake(old);
        if (old instanceof LivingEntity oldLiving) freshenForSummon(oldLiving);
        old.setYRot(player.getYRot());
        old.teleportTo(spawnPos.x, spawnPos.y, spawnPos.z);

        // This probably isn't necessary, but in the case that a chunk crashes while the tp is happening this will guarantee de-duping the entity.
        int newRevision = saved.incrementRevision(bondId);
        old.setData(ModAttachments.BONDED.get(), old.getData(ModAttachments.BONDED.get()).withRevision(newRevision));

        playSummonFx(level, spawnPos.x, spawnPos.y, spawnPos.z, true);

        BondRoster currentRoster = player.getData(ModAttachments.BOND_ROSTER.get());
        Optional<Bond> currentBond = currentRoster.get(bondId);
        if (currentBond.isPresent()) {
            Bond updated = currentBond.get()
                    .withRevision(newRevision)
                    .withLastSummonedAt(System.currentTimeMillis());
            player.setData(ModAttachments.BOND_ROSTER.get(), currentRoster.with(updated));
        }

        GlobalSummonCooldownTracker.get().recordSummon(player.getUUID());
        return SummonResult.TELEPORTED_NEAR;
    }

    private static SummonResult teleportLoaded(ServerPlayer player, Entity old, Bond bond, ServerLevel targetLevel, KindredSavedData saved, SummonResult successResult) {
        UUID bondId = bond.bondId();

        Vec3 spawnPos;
        if (Config.REQUIRE_SPACE.get()) {
            Optional<Vec3> found = findSpawnLocation(targetLevel, player, old.getType().getDimensions());
            if (found.isEmpty()) return SummonResult.NO_SPACE;
            spawnPos = found.get();
        } else {
            spawnPos = player.position();
        }

        wake(old);

        DimensionTransition transition = new DimensionTransition(
                targetLevel,
                spawnPos,
                Vec3.ZERO,
                player.getYRot(),
                old.getXRot(),
                DimensionTransition.DO_NOTHING);

        Entity teleported;
        try {
            teleported = old.changeDimension(transition);
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

        int newRevision = saved.incrementRevision(bondId);
        teleported.setData(ModAttachments.BONDED.get(), teleported.getData(ModAttachments.BONDED.get()).withRevision(newRevision));

        playSummonFx(targetLevel, spawnPos.x, spawnPos.y, spawnPos.z, true);

        BondRoster currentRoster = player.getData(ModAttachments.BOND_ROSTER.get());
        Optional<Bond> currentBond = currentRoster.get(bondId);
        if (currentBond.isPresent()) {
            CompoundTag freshNbt = teleported.saveWithoutId(new CompoundTag());
            Bond updated = currentBond.get()
                    .withSnapshot(freshNbt, targetLevel.dimension(), spawnPos)
                    .withRevision(newRevision)
                    .withLastSummonedAt(System.currentTimeMillis())
                    .withDiedAt(Optional.empty())
                    .withDismissed(false);
            player.setData(ModAttachments.BOND_ROSTER.get(), currentRoster.with(updated));
        }

        GlobalSummonCooldownTracker.get().recordSummon(player.getUUID());

        return successResult;
    }

    private static SummonResult materializeFresh(ServerPlayer player, Bond bond, ServerLevel targetLevel, KindredSavedData saved) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(bond.entityType());
        if (type == null) {
            Kindred.LOGGER.warn("[kindred] SPAWN_FAILED: entity type {} not in registry (bond {} for {})",
                    bond.entityType(), bond.bondId(), player.getGameProfile().getName());
            return SummonResult.SPAWN_FAILED;
        }

        // Find a valid spawn pocket within 5x5 of the player; snap to ground.
        Vec3 spawnPos;
        if (Config.REQUIRE_SPACE.get()) {
            Optional<Vec3> found = findSpawnLocation(targetLevel, player, type.getDimensions());
            if (found.isEmpty()) return SummonResult.NO_SPACE;
            spawnPos = found.get();
        } else {
            spawnPos = player.position();
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

        wake(entity);

        entity.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

        int newRevision = saved.incrementRevision(bond.bondId());
        entity.setData(ModAttachments.BONDED.get(), new Bonded(bond.bondId(), player.getUUID(), newRevision));

        if (!targetLevel.addFreshEntity(entity)) {
            Kindred.LOGGER.warn("[kindred] SPAWN_FAILED: addFreshEntity rejected {} at {} in {} (bond {} for {})",
                    bond.entityType(), spawnPos, targetLevel.dimension().location(),
                    bond.bondId(), player.getGameProfile().getName());
            return SummonResult.SPAWN_FAILED;
        }

        playSummonFx(targetLevel, spawnPos.x, spawnPos.y, spawnPos.z, true);

        BondRoster currentRoster = player.getData(ModAttachments.BOND_ROSTER.get());
        Bond updated = bond.withRevision(newRevision)
                .withLastSummonedAt(System.currentTimeMillis())
                .withDiedAt(Optional.empty())  // successful materialize IS the revival
                .withDismissed(false);         // entity is back in the world
        player.setData(ModAttachments.BOND_ROSTER.get(), currentRoster.with(updated));

        GlobalSummonCooldownTracker.get().recordSummon(player.getUUID());

        return SummonResult.SUMMONED_FRESH;
    }

    public static void applyDisplayName(Entity entity, Optional<String> displayName) {
        if (displayName.isPresent()) {
            entity.setCustomName(Component.literal(displayName.get()));
            entity.setCustomNameVisible(true);
        } else {
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
        }
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

    private static void wake(Entity entity) {
        if (entity instanceof TamableAnimal tame) {
            tame.setOrderedToSit(false);
            tame.setInSittingPose(false);
        }
    }

    private static void writeSummonTimestamp(ServerPlayer player, Bond bond) {
        // Re-read the roster fresh — we don't hold a reference across the call
        // chain that got us here, and the player's roster may have been touched
        // by a sibling packet handler on the same server tick.
        // Successful summon also clears any pending revival cooldown — this IS the revival.
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        Bond updated = bond.withLastSummonedAt(System.currentTimeMillis()).withDiedAt(Optional.empty());
        player.setData(ModAttachments.BOND_ROSTER.get(), roster.with(updated));
    }

    private static boolean isPlayerGrounded(ServerPlayer player) {
        if (player.onGround()) return true;
        if (Config.ALLOW_WATER_SUMMON.get() && player.isInWater()) return true;
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos check = feet.below(dy);
            BlockState state = level.getBlockState(check);
            if (state.isFaceSturdy(level, check, Direction.UP)) return true;
        }
        return false;
    }

    private static Optional<Vec3> findSpawnLocation(ServerLevel level, ServerPlayer player, EntityDimensions dims) {
        BlockPos pp = player.blockPosition();

        float yawRad = player.getYRot() * (float) (Math.PI / 180.0);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        record SpawnLocationCandidate(int dx, int dz, double score) {}
        List<SpawnLocationCandidate> ranked = new ArrayList<>(24);

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                double forwardness = dx * fx + dz * fz;
                double lateral = Math.abs(dx * fz - dz * fx);
                double dist = Math.sqrt(dx * dx + dz * dz);
                ranked.add(new SpawnLocationCandidate(dx, dz, forwardness - 0.2 * lateral + 0.05 * dist));
            }
        }
        ranked.sort(Comparator.comparingDouble(SpawnLocationCandidate::score).reversed());

        for (SpawnLocationCandidate candidate : ranked) {
            Optional<Vec3> spot = tryColumn(level, pp.offset(candidate.dx, 0, candidate.dz), dims, false);
            if (spot.isPresent()) return spot;
        }
        Optional<Vec3> dryOnPlayer = tryColumn(level, pp, dims, false);
        if (dryOnPlayer.isPresent()) return dryOnPlayer;

        for (SpawnLocationCandidate c : ranked) {
            Optional<Vec3> spot = tryColumn(level, pp.offset(c.dx, 0, c.dz), dims, true);
            if (spot.isPresent()) return spot;
        }
        Optional<Vec3> wetOnPlayer = tryColumn(level, pp, dims, true);
        if (wetOnPlayer.isPresent()) return wetOnPlayer;

        if (Config.ALLOW_WATER_SUMMON.get() && player.isInWater()) {
            return Optional.of(player.position());
        }
        return Optional.empty();
    }

    private static Optional<Vec3> tryColumn(ServerLevel level, BlockPos start, EntityDimensions dims, boolean allowWater) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (int dy = -1; dy <= 3; dy++) {
            int feetY = start.getY() - dy;
            if (feetY <= minY || feetY >= maxY) continue;
            BlockPos top = new BlockPos(start.getX(), feetY, start.getZ());
            BlockPos floor = top.below();
            BlockState floorState = level.getBlockState(floor);
            if (!floorState.isFaceSturdy(level, floor, Direction.UP)) continue;
            if (isHazardousFloor(floorState)) return Optional.empty();
            AABB box = dims.makeBoundingBox(top.getX() + 0.5D, top.getY(), top.getZ() + 0.5D);
            if (!level.noCollision(box)) return Optional.empty();
            if (hasLavaInOrNear(level, box)) return Optional.empty();
            if (!allowWater && hasFluidInPocket(level, box, FluidTags.WATER)) return Optional.empty();
            return Optional.of(new Vec3(top.getX() + 0.5D, top.getY(), top.getZ() + 0.5D));
        }
        return Optional.empty();
    }

    private static boolean isHazardousFloor(BlockState state) {
        var block = state.getBlock();
        if (block == Blocks.MAGMA_BLOCK) return true;
        if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) return true;
        if ((block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE)
                && state.getValue(CampfireBlock.LIT)) return true;
        return false;
    }

    private static boolean hasLavaInOrNear(ServerLevel level, AABB box) {
        AABB scan = box.inflate(1.0D, 0.5D, 1.0D);
        BlockPos min = BlockPos.containing(scan.minX, scan.minY, scan.minZ);
        BlockPos max = BlockPos.containing(scan.maxX - 1.0E-7D, scan.maxY - 1.0E-7D, scan.maxZ - 1.0E-7D);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (level.getFluidState(p).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    private static boolean hasFluidInPocket(ServerLevel level, AABB box, net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> fluidTag) {
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX - 1.0E-7D, box.maxY - 1.0E-7D, box.maxZ - 1.0E-7D);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (level.getFluidState(p).is(fluidTag)) return true;
        }
        return false;
    }

    private BondService() {}
}
