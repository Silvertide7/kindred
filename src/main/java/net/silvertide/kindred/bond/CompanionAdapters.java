package net.silvertide.kindred.bond;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.neoforged.fml.ModList;
import net.silvertide.kindred.compat.FamiliarsLibCompat;
import net.silvertide.kindred.compat.JurassicRebornCompat;
import net.silvertide.kindred.compat.PettingCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CompanionAdapters {
    private static final List<CompanionAdapter> ADAPTERS = buildAdapters();

    private static List<CompanionAdapter> buildAdapters() {
        List<CompanionAdapter> adapters = new ArrayList<>();
        adapters.add(new VanillaOwnableAdapter());
        if (ModList.get().isLoaded(JurassicRebornCompat.MODID)) adapters.add(JurassicRebornCompat.adapter());
        if (ModList.get().isLoaded(PettingCompat.MODID)) adapters.add(new PettingCompat());
        if (ModList.get().isLoaded(FamiliarsLibCompat.MODID)) adapters.add(FamiliarsLibCompat.adapter());
        return List.copyOf(adapters);
    }

    public static boolean isOwnable(Entity entity) {
        return ADAPTERS.stream().anyMatch(adapter -> adapter.handles(entity));
    }

    public static Optional<UUID> ownerOf(Entity entity) {
        return ADAPTERS.stream()
                .filter(adapter -> adapter.handles(entity))
                .flatMap(adapter -> adapter.ownerOf(entity).stream())
                .findFirst();
    }

    public static void wake(Entity entity) {
        entity.setShiftKeyDown(false);
        ADAPTERS.forEach(adapter -> adapter.wake(entity));
    }

    private static final class VanillaOwnableAdapter implements CompanionAdapter {
        @Override
        public boolean handles(Entity entity) {
            return entity instanceof OwnableEntity;
        }

        @Override
        public Optional<UUID> ownerOf(Entity entity) {
            return Optional.ofNullable(((OwnableEntity) entity).getOwnerUUID());
        }

        @Override
        public void wake(Entity entity) {
            if (entity instanceof TamableAnimal tame) {
                tame.setOrderedToSit(false);
                tame.setInSittingPose(false);
            }
        }
    }

    private CompanionAdapters() {}
}
