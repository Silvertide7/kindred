package net.silvertide.kindred.compat;

import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.world.entity.Entity;
import net.silvertide.kindred.bond.CompanionAdapter;

import java.util.Optional;
import java.util.UUID;

public final class FamiliarsLibCompat implements CompanionAdapter {
    public static final String MODID = "familiarslib";

    @Override
    public boolean handles(Entity entity) {
        return entity instanceof AbstractSpellCastingPet;
    }

    @Override
    public Optional<UUID> ownerOf(Entity entity) {
        return Optional.ofNullable(((AbstractSpellCastingPet) entity).getOwnerUUID());
    }

    @Override
    public void wake(Entity entity) {
        if (entity instanceof AbstractSpellCastingPet familiar) familiar.setSitting(false);
    }
}
