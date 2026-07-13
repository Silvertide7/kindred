package net.silvertide.kindred.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

@Cancelable
public class BondClaimEvent extends Event {
    private final ServerPlayer player;
    private final Entity target;

    public BondClaimEvent(ServerPlayer player, Entity target) {
        this.player = player;
        this.target = target;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Entity getTarget() {
        return target;
    }
}
