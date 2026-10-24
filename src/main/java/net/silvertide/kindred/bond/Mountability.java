package net.silvertide.kindred.bond;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PlayerRideable;
import net.minecraft.world.entity.Saddleable;
import net.silvertide.kindred.registry.ModTags;

public final class Mountability {
    private Mountability() {}

    public static boolean isMountable(Entity entity) {
        return (entity instanceof PlayerRideable || entity instanceof Saddleable)
                && !entity.getType().is(ModTags.AUTO_MOUNT_DENYLIST);
    }
}
