package net.silvertide.kindred.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.silvertide.kindred.Kindred;

public final class ModTags {
    public static final TagKey<EntityType<?>> BOND_BLOCKLIST = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Kindred.MODID, "bond_blocklist")
    );

    private ModTags() {}
}
