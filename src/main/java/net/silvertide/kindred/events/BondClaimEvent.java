package net.silvertide.kindred.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class BondClaimEvent extends Event implements ICancellableEvent {
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
