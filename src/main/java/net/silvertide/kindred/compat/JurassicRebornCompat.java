package net.silvertide.kindred.compat;

import net.minecraft.world.entity.Entity;
import net.silvertide.kindred.bond.CompanionAdapter;
import net.vit.jurassicreborn.common.entities.DinosaurEntity;

import java.util.Optional;
import java.util.UUID;

public final class JurassicRebornCompat implements CompanionAdapter {
    public static final String MODID = "jurassicreborn";

    @Override
    public boolean handles(Entity entity) {
        return entity instanceof DinosaurEntity;
    }

    @Override
    public Optional<UUID> ownerOf(Entity entity) {
        return Optional.ofNullable(((DinosaurEntity) entity).getOwner());
    }

    @Override
    public void wake(Entity entity) {
        if (!(entity instanceof DinosaurEntity dinosaur)) return;
        dinosaur.disturbSleep();
        dinosaur.setFieldOrder(DinosaurEntity.Order.FOLLOW);
    }
}
