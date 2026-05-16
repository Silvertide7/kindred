package net.silvertide.kindred.bond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.silvertide.kindred.config.Config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class BondSpawnLocator {
    private BondSpawnLocator() {}

    private static final int SEARCH_RADIUS_BLOCKS = 2;
    private static final int GROUND_PROBE_DEPTH_BLOCKS = 2;
    private static final int COLUMN_PROBE_MIN_DY = -1;
    private static final int COLUMN_PROBE_MAX_DY = 3;
    private static final double LATERAL_PENALTY_WEIGHT = 0.2;
    private static final double DISTANCE_BIAS_WEIGHT = 0.05;
    private static final double LAVA_BUFFER_HORIZONTAL = 1.0D;
    private static final double LAVA_BUFFER_VERTICAL = 0.5D;
    private static final double BLOCK_POS_EPSILON = 1.0E-7D;

    private record ColumnCandidate(int dx, int dz, double score) {}

    public static boolean isPlayerGrounded(ServerPlayer player) {
        if (player.onGround()) return true;
        if (Config.ALLOW_WATER_SUMMON.get() && player.isInWater()) return true;

        Level level = player.level();
        BlockPos feet = player.blockPosition();
        for (int dy = 0; dy <= GROUND_PROBE_DEPTH_BLOCKS; dy++) {
            BlockPos checkPos = feet.below(dy);
            BlockState state = level.getBlockState(checkPos);
            if (state.isFaceSturdy(level, checkPos, Direction.UP)) return true;
        }
        return false;
    }

    public static Optional<Vec3> findSpawnLocation(ServerLevel level, ServerPlayer player, EntityDimensions dims) {
        BlockPos playerPos = player.blockPosition();
        List<ColumnCandidate> rankedCandidates = rankCandidatesAroundPlayer(player);

        Optional<Vec3> dryNearby = firstViableColumn(level, playerPos, dims, rankedCandidates, false);
        if (dryNearby.isPresent()) return dryNearby;

        Optional<Vec3> wetNearby = firstViableColumn(level, playerPos, dims, rankedCandidates, true);
        if (wetNearby.isPresent()) return wetNearby;

        if (Config.ALLOW_WATER_SUMMON.get() && player.isInWater()) {
            return Optional.of(player.position());
        }
        return Optional.empty();
    }

    private static List<ColumnCandidate> rankCandidatesAroundPlayer(ServerPlayer player) {
        float yawRadians = player.getYRot() * (float) (Math.PI / 180.0);
        double forwardX = -Math.sin(yawRadians);
        double forwardZ = Math.cos(yawRadians);

        List<ColumnCandidate> ranked = new ArrayList<>();
        for (int dx = -SEARCH_RADIUS_BLOCKS; dx <= SEARCH_RADIUS_BLOCKS; dx++) {
            for (int dz = -SEARCH_RADIUS_BLOCKS; dz <= SEARCH_RADIUS_BLOCKS; dz++) {
                if (dx == 0 && dz == 0) continue;
                double forwardness = dx * forwardX + dz * forwardZ;
                double lateral = Math.abs(dx * forwardZ - dz * forwardX);
                double distance = Math.sqrt(dx * dx + dz * dz);
                double score = forwardness
                        - LATERAL_PENALTY_WEIGHT * lateral
                        + DISTANCE_BIAS_WEIGHT * distance;
                ranked.add(new ColumnCandidate(dx, dz, score));
            }
        }
        ranked.sort(Comparator.comparingDouble(ColumnCandidate::score).reversed());
        return ranked;
    }

    private static Optional<Vec3> firstViableColumn(ServerLevel level, BlockPos playerPos, EntityDimensions dims,
                                                    List<ColumnCandidate> rankedCandidates, boolean allowWater) {
        for (ColumnCandidate candidate : rankedCandidates) {
            Optional<Vec3> spot = tryColumn(level, playerPos.offset(candidate.dx, 0, candidate.dz), dims, allowWater);
            if (spot.isPresent()) return spot;
        }
        return tryColumn(level, playerPos, dims, allowWater);
    }

    private static Optional<Vec3> tryColumn(ServerLevel level, BlockPos start, EntityDimensions dims, boolean allowWater) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (int dy = COLUMN_PROBE_MIN_DY; dy <= COLUMN_PROBE_MAX_DY; dy++) {
            int feetY = start.getY() - dy;
            if (feetY <= minY || feetY >= maxY) continue;
            BlockPos top = new BlockPos(start.getX(), feetY, start.getZ());
            BlockPos floor = top.below();
            BlockState floorState = level.getBlockState(floor);
            if (!floorState.isFaceSturdy(level, floor, Direction.UP)) continue;
            if (isHazardousFloor(floorState)) return Optional.empty();
            AABB bbox = dims.makeBoundingBox(top.getX() + 0.5D, top.getY(), top.getZ() + 0.5D);
            if (!level.noCollision(bbox)) return Optional.empty();
            if (hasLavaInOrNear(level, bbox)) return Optional.empty();
            if (!allowWater && hasFluidInPocket(level, bbox, FluidTags.WATER)) return Optional.empty();
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

    private static boolean hasLavaInOrNear(ServerLevel level, AABB bbox) {
        AABB scanRegion = bbox.inflate(LAVA_BUFFER_HORIZONTAL, LAVA_BUFFER_VERTICAL, LAVA_BUFFER_HORIZONTAL);
        BlockPos min = BlockPos.containing(scanRegion.minX, scanRegion.minY, scanRegion.minZ);
        BlockPos max = BlockPos.containing(
                scanRegion.maxX - BLOCK_POS_EPSILON,
                scanRegion.maxY - BLOCK_POS_EPSILON,
                scanRegion.maxZ - BLOCK_POS_EPSILON);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (level.getFluidState(p).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    private static boolean hasFluidInPocket(ServerLevel level, AABB bbox, TagKey<Fluid> fluidTag) {
        BlockPos min = BlockPos.containing(bbox.minX, bbox.minY, bbox.minZ);
        BlockPos max = BlockPos.containing(
                bbox.maxX - BLOCK_POS_EPSILON,
                bbox.maxY - BLOCK_POS_EPSILON,
                bbox.maxZ - BLOCK_POS_EPSILON);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (level.getFluidState(p).is(fluidTag)) return true;
        }
        return false;
    }
}
