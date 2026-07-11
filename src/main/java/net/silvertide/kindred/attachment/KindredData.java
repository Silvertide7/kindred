package net.silvertide.kindred.attachment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.silvertide.kindred.Kindred;

import java.util.Optional;

public final class KindredData {
    private static final String ROSTER_KEY = Kindred.MODID + ":bond_roster";
    private static final String ROSTER_QUARANTINE_KEY = Kindred.MODID + ":bond_roster_corrupt";
    private static final String BONDED_KEY = Kindred.MODID + ":bonded";

    public static BondRoster getRoster(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(ROSTER_KEY)) return BondRoster.EMPTY;
        var parsed = BondRoster.CODEC.parse(NbtOps.INSTANCE, persisted.get(ROSTER_KEY));
        parsed.error().ifPresent(error -> quarantineCorruptRoster(player, persisted, error.message()));
        return parsed.resultOrPartial(message -> {}).orElse(BondRoster.EMPTY);
    }

    private static void quarantineCorruptRoster(Player player, CompoundTag persisted, String errorMessage) {
        if (persisted.contains(ROSTER_QUARANTINE_KEY)) return;
        Kindred.LOGGER.error(
                "[kindred] Could not fully decode the bond roster for {} ({}); preserving the raw data under '{}' so it is not overwritten",
                player.getGameProfile().getName(), errorMessage, ROSTER_QUARANTINE_KEY);
        persisted.put(ROSTER_QUARANTINE_KEY, persisted.get(ROSTER_KEY).copy());
    }

    public static void setRoster(Player player, BondRoster roster) {
        Tag encoded = BondRoster.CODEC.encodeStart(NbtOps.INSTANCE, roster)
                .resultOrPartial(Kindred.LOGGER::error)
                .orElseGet(CompoundTag::new);
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(ROSTER_KEY, encoded);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static Optional<Bonded> getBonded(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(BONDED_KEY)) return Optional.empty();
        return Bonded.CODEC.parse(NbtOps.INSTANCE, data.get(BONDED_KEY))
                .resultOrPartial(Kindred.LOGGER::error);
    }

    public static boolean isBonded(Entity entity) {
        return entity.getPersistentData().contains(BONDED_KEY);
    }

    public static void setBonded(Entity entity, Bonded bonded) {
        Bonded.CODEC.encodeStart(NbtOps.INSTANCE, bonded)
                .resultOrPartial(Kindred.LOGGER::error)
                .ifPresent(tag -> entity.getPersistentData().put(BONDED_KEY, tag));
    }

    public static void removeBonded(Entity entity) {
        entity.getPersistentData().remove(BONDED_KEY);
    }

    private KindredData() {}
}
