package net.silvertide.petsummon.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.silvertide.petsummon.PetSummon;

public final class ModTags {
    public static final TagKey<EntityType<?>> BOND_BLOCKLIST = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(PetSummon.MODID, "bond_blocklist")
    );

    private ModTags() {}
}
