package net.silvertide.kindred.compat.pmmo;

import net.minecraft.world.entity.player.Player;

public interface PmmoBridge {
    long getSkillLevel(Player player, String skill);
}
