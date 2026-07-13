package net.silvertide.kindred.client.data;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.silvertide.kindred.Kindred;
import net.silvertide.kindred.network.BondView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PreviewEntityCache {
    private static final int ABSTRACT_HORSE_FLAG_SADDLE = 4;

    private static final Map<UUID, LivingEntity> cache = new HashMap<>();

    public static LivingEntity getOrBuild(BondView view) {
        LivingEntity cached = cache.get(view.bondId());
        if (cached != null) return cached;
        LivingEntity built = build(view);
        if (built != null) cache.put(view.bondId(), built);
        return built;
    }

    private static LivingEntity build(BondView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(view.entityType());
        if (type == null) return null;
        Entity raw;
        try {
            raw = type.create(mc.level);
        } catch (Throwable t) {
            Kindred.LOGGER.warn("[kindred] Failed to construct preview entity for {}: {}", view.entityType(), t.getMessage());
            return null;
        }
        if (!(raw instanceof LivingEntity living)) return null;
        try {
            raw.load(view.nbtSnapshot());
        } catch (Throwable t) {
            Kindred.LOGGER.warn("[kindred] Failed to load NBT for preview entity {}: {}", view.entityType(), t.getMessage());
            return null;
        }
        freshenForPreview(living, view.nbtSnapshot());
        return living;
    }

    private static void freshenForPreview(LivingEntity living, CompoundTag nbt) {
        living.setHealth(living.getMaxHealth());
        living.hurtTime = 0;
        living.deathTime = 0;
        living.clearFire();
        living.removeAllEffects();
        living.setAirSupply(living.getMaxAirSupply());
        living.fallDistance = 0F;
        living.setTicksFrozen(0);
        living.setCustomNameVisible(false);

        if (living instanceof AbstractHorse horse) {
            boolean saddled = nbt.contains("SaddleItem", Tag.TAG_COMPOUND);
            horse.setFlag(ABSTRACT_HORSE_FLAG_SADDLE, saddled);
        }
    }

    public static void clear() {
        cache.clear();
    }

    private PreviewEntityCache() {}
}
