package net.silvertide.kindred.compat.pmmo;

import harmonised.pmmo.api.APIUtils;
import net.minecraft.world.entity.player.Player;

public final class PmmoBridgeImpl implements PmmoBridge {
    @Override
    public long getSkillLevel(Player player, String skill) {
        return APIUtils.getLevel(skill, player);
    }
}
