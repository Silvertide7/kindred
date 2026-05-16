package net.silvertide.kindred.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.silvertide.kindred.Kindred;

public final class ModTags {
    private ModTags() {}

    public static final TagKey<EntityType<?>> BOND_ALLOWLIST = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "bond_allowlist")
    );

    public static final TagKey<EntityType<?>> BOND_DENYLIST = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "bond_denylist")
    );

    public static final TagKey<DimensionType> NO_SUMMON_DIMENSIONS = TagKey.create(
            Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "no_summon_dimensions")
    );

    public static final TagKey<Biome> NO_SUMMON_BIOMES = TagKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "no_summon_biomes")
    );
}
