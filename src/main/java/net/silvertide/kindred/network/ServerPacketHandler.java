package net.silvertide.kindred.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.silvertide.kindred.attachment.Bond;
import net.silvertide.kindred.attachment.BondRoster;
import net.silvertide.kindred.bond.HoldManager;
import net.silvertide.kindred.bond.bond_results.ClaimResult;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.bond.BondEntityIndex;
import net.silvertide.kindred.network.packet.C2SCancelHold;
import net.silvertide.kindred.network.packet.C2SCheckBindCandidate;
import net.silvertide.kindred.network.packet.C2SClaimEntity;
import net.silvertide.kindred.network.packet.C2SOpenRoster;
import net.silvertide.kindred.network.packet.C2SRenameBond;
import net.silvertide.kindred.network.packet.C2SReorderBond;
import net.silvertide.kindred.network.packet.C2SRequestHold;
import net.silvertide.kindred.network.packet.C2SSetActivePet;
import net.silvertide.kindred.network.packet.S2CBindCandidateResult;
import net.silvertide.kindred.network.packet.S2CRosterSync;
import net.silvertide.kindred.registry.ModAttachments;
import net.silvertide.kindred.bond.BondService;
import net.silvertide.kindred.bond.GlobalSummonCooldownTracker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ServerPacketHandler {

    /** Server-side guard against spoofed claim packets. Client raycast caps at 8. */
    private static final double MAX_CLAIM_DISTANCE_SQ = 12.0D * 12.0D;

    public static void onOpenRoster(C2SOpenRoster payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            sendRosterSync(player);
        });
    }

    /**
     * Handle {@link C2SRequestHold}: validate via {@link HoldManager#requestStart},
     * which either registers the hold and pushes {@code S2CHoldStart} to the
     * client, or surfaces a deny via vanilla {@code displayClientMessage}. All
     * the eligibility logic lives in {@code HoldEligibility} — this handler is
     * just a packet → service shim.
     */
    public static void onRequestHold(C2SRequestHold payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            HoldManager.get().requestStart(player, payload.action(), payload.bondId().orElse(null));
        });
    }

    /**
     * Handle {@link C2SCancelHold}: drop the player's hold and push
     * {@code S2CHoldStop}. {@link HoldManager#cancel} is idempotent, so calls
     * for players with no active hold (e.g. a release packet that arrives
     * after the server-side completion handler) are silent no-ops.
     */
    public static void onCancelHold(C2SCancelHold payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            HoldManager.get().cancel(player);
        });
    }

    public static void onCheckBindCandidate(C2SCheckBindCandidate payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = (ServerLevel) player.level();
            Entity target = level.getEntity(payload.entityUUID());
            // Entity gone or out of reach — silent rejection (no message). The screen
            // falls back to the generic "look at a tamed pet" hint in that case.
            if (target == null || target.distanceToSqr(player) > MAX_CLAIM_DISTANCE_SQ) {
                PacketDistributor.sendToPlayer(player, new S2CBindCandidateResult(
                        payload.entityUUID(), false, Optional.empty()));
                return;
            }
            ClaimResult result = BondService.checkClaimEligibility(player, target);
            boolean canBind = result == ClaimResult.CLAIMED;
            Optional<String> denyKey = canBind ? Optional.empty() : Optional.of(denyKeyFor(result));
            PacketDistributor.sendToPlayer(player, new S2CBindCandidateResult(
                    payload.entityUUID(), canBind, denyKey));
        });
    }

    private static String denyKeyFor(ClaimResult result) {
        return switch (result) {
            case NOT_OWNABLE -> "kindred.bind.deny.not_ownable";
            case NOT_OWNED_BY_PLAYER -> "kindred.bind.deny.not_owned";
            case NOT_ALLOWED -> "kindred.bind.deny.not_allowed";
            case REQUIRES_SADDLEABLE -> "kindred.bind.deny.requires_saddleable";
            case AT_CAPACITY -> "kindred.bind.deny.at_capacity";
            case ALREADY_BONDED -> "kindred.bind.deny.already_bonded";
            case NOT_ENOUGH_XP -> "kindred.bind.deny.not_enough_xp";
            case PMMO_LOCKED -> "kindred.bind.deny.pmmo_locked";
            case CANCELLED -> "kindred.bind.deny.cancelled";
            default -> "kindred.bind.deny.generic";
        };
    }

    public static void onClaimEntity(C2SClaimEntity payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = (ServerLevel) player.level();
            Entity target = level.getEntity(payload.entityUUID());
            if (target == null || target.distanceToSqr(player) > MAX_CLAIM_DISTANCE_SQ) {
                // Silent reject — the screen footer already handled the deny, and
                // by the time the player clicked Bind they were aiming at a
                // confirmed candidate. Reaching here means the entity moved away
                // or unloaded between confirm and click; nothing actionable.
                return;
            }
            ClaimResult result = BondService.tryClaim(player, target);
            if (result == ClaimResult.CLAIMED) {
                player.sendSystemMessage(Component.translatable(
                        "kindred.bind.success", target.getType().getDescription()));
                sendRosterSync(player);
            } else {
                // Failure path: surface the same translated deny reason the screen
                // footer would show. Args for the arg-bearing keys are interpolated
                // here so the chat reads "Requires Charisma level 3" instead of
                // "Requires %1$s level %2$s".
                player.sendSystemMessage(claimDenyMessage(result));
            }
        });
    }

    private static Component claimDenyMessage(ClaimResult result) {
        String key = denyKeyFor(result);
        return switch (result) {
            case NOT_ENOUGH_XP ->
                    Component.translatable(key, Config.BOND_XP_LEVEL_COST.get());
            case PMMO_LOCKED -> Component.translatable(
                    key,
                    Component.translatable("pmmo." + Config.PMMO_SKILL.get()),
                    Config.PMMO_START_LEVEL.get());
            default -> Component.translatable(key);
        };
    }

    private static final int MAX_NAME_LEN = 32;

    public static void onRenameBond(C2SRenameBond payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
            Optional<Bond> bond = roster.get(payload.bondId());
            if (bond.isEmpty()) return;
            Optional<String> sanitized = payload.newName()
                    .map(ServerPacketHandler::sanitizeName)
                    .filter(s -> !s.isEmpty());
            Bond updated = bond.get().withDisplayName(sanitized);
            player.setData(ModAttachments.BOND_ROSTER.get(), roster.with(updated));
            // Mirror the rename onto the live entity so the in-world nametag updates
            // immediately. Offline pets pick this up on next materialize from displayName.
            BondEntityIndex.get().find(payload.bondId())
                    .ifPresent(e -> BondService.applyDisplayName(e, sanitized));
            sendRosterSync(player);
        });
    }

    private static String sanitizeName(String raw) {
        if (raw == null) return "";
        String s = raw.replace("§", "");        // strip Minecraft formatting codes
        s = s.replaceAll("\\p{Cntrl}", "");          // strip control chars (newlines, tabs, etc.)
        s = s.trim();
        if (s.length() > MAX_NAME_LEN) s = s.substring(0, MAX_NAME_LEN);
        return s;
    }

    public static void onReorderBond(C2SReorderBond payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
            BondRoster updated = roster.withMoved(payload.bondId(), payload.delta());
            if (updated != roster) {
                player.setData(ModAttachments.BOND_ROSTER.get(), updated);
                sendRosterSync(player);
            }
        });
    }

    public static void onSetActivePet(C2SSetActivePet payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
            BondRoster updated = roster.withActive(payload.bondId());
            if (updated != roster) {
                player.setData(ModAttachments.BOND_ROSTER.get(), updated);
            }
            sendRosterSync(player);
        });
    }

    public static void sendRosterSync(ServerPlayer player) {
        BondRoster roster = player.getData(ModAttachments.BOND_ROSTER.get());
        long now = System.currentTimeMillis();
        long cooldownMs = Config.SUMMON_COOLDOWN_TICKS.get() * 50L;
        long revivalCooldownMs = Config.revivalCooldownMs();
        // Use the LinkedHashMap's insertion order — that's the player's chosen row
        // order (mutated via C2SReorderBond and preserved across save/load).
        List<BondView> views = roster.bonds().values().stream()
                .map(b -> {
                    long remaining = Math.max(0L, cooldownMs - (now - b.lastSummonedAt()));
                    long revivalRemaining = 0L;
                    if (revivalCooldownMs > 0L && b.diedAt().isPresent()) {
                        revivalRemaining = Math.max(0L, revivalCooldownMs - (now - b.diedAt().get()));
                    }
                    Optional<Entity> live = BondEntityIndex.get().find(b.bondId());
                    boolean loaded = live.isPresent();
                    // Capture live NBT for loaded pets so saddle/armor/equipment changes
                    // made in-world flow into the preview without waiting for the pet
                    // to leave its chunk (which is when the cached snapshot refreshes).
                    CompoundTag nbt = loaded
                            ? live.get().saveWithoutId(new CompoundTag())
                            : b.nbtSnapshot();
                    return BondView.from(b, roster.isActive(b.bondId()), loaded, remaining, revivalRemaining, nbt);
                })
                .toList();
        long globalRemaining = GlobalSummonCooldownTracker.get()
                .remainingMs(player.getUUID(), Config.summonGlobalCooldownMs());
        int effectiveCap = BondService.effectiveMaxBonds(player);
        PacketDistributor.sendToPlayer(player, new S2CRosterSync(views, globalRemaining, effectiveCap));
    }

    private ServerPacketHandler() {}
}
