package net.silvertide.kindred.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.silvertide.kindred.client.data.ClientRosterData;
import net.silvertide.kindred.compat.pmmo.PmmoMode;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.network.packet.C2SCheckBindCandidate;
import net.silvertide.kindred.network.packet.C2SClaimEntity;

import java.util.Optional;
import java.util.UUID;

import static net.silvertide.kindred.client.screen.ScreenDrawUtil.drawButton;
import static net.silvertide.kindred.client.screen.ScreenDrawUtil.inBox;

public final class BindFooter {
    private static final double CLAIM_RAYCAST_DISTANCE = 8.0D;
    private static final long BIND_HOLD_MS = 1000L;
    private static final int BUTTON_HEIGHT = 20;

    private static final int C_TEXT_MUTED = 0xFF8FA0B0;
    private static final int C_BTN_CLAIM = 0xFF3D5C8A;
    private static final int C_BTN_CLAIM_HOVER = 0xFF5278B0;
    private static final int C_XP_COST_UNAFFORDABLE = 0xFFE57878;

    private Entity raycastCandidate;
    private Boolean serverBindEligibility;
    private Optional<String> serverDenyKey = Optional.empty();
    private long holdStartMs = 0L;

    private int buttonX;
    private int buttonAnchorY;
    private int buttonW;
    private int costLineY;

    public void configureLayout(int buttonX, int buttonAnchorY, int buttonW, int costLineY) {
        this.buttonX = buttonX;
        this.buttonAnchorY = buttonAnchorY;
        this.buttonW = buttonW;
        this.costLineY = costLineY;
    }

    public void onOpen(LocalPlayer player) {
        if (player == null) return;
        Entity hit = raycastForBindCandidate(player);
        if (hit == null || !passesClientGates(hit)) return;
        raycastCandidate = hit;
        serverBindEligibility = Boolean.FALSE;
        serverDenyKey = Optional.empty();
        PacketDistributor.sendToServer(new C2SCheckBindCandidate(hit.getUUID()));
    }

    public void onClose() {
        holdStartMs = 0L;
    }

    public void onMouseReleased() {
        holdStartMs = 0L;
    }

    public void onCandidateResult(UUID entityUuid, boolean canBind, Optional<String> denyKeyFromServer) {
        if (raycastCandidate == null) return;
        if (!entityUuid.equals(raycastCandidate.getUUID())) return;
        serverBindEligibility = canBind ? Boolean.TRUE : Boolean.FALSE;
        serverDenyKey = canBind ? Optional.empty() : denyKeyFromServer;
    }

    public boolean tryHandleClick(int mouseX, int mouseY) {
        if (!inBox(mouseX, mouseY, buttonX, buttonY(), buttonW, BUTTON_HEIGHT)) return false;
        if (confirmedClaimCandidate() == null) return false;
        holdStartMs = System.currentTimeMillis();
        return true;
    }

    public void tickHold(int mouseX, int mouseY) {
        if (holdStartMs == 0L) return;

        Entity candidate = confirmedClaimCandidate();
        if (candidate == null) {
            holdStartMs = 0L;
            return;
        }
        if (!inBox(mouseX, mouseY, buttonX, buttonY(), buttonW, BUTTON_HEIGHT)) {
            holdStartMs = 0L;
            return;
        }
        if (System.currentTimeMillis() - holdStartMs >= BIND_HOLD_MS) {
            PacketDistributor.sendToServer(new C2SClaimEntity(candidate.getUUID()));
            raycastCandidate = null;
            serverBindEligibility = null;
            serverDenyKey = Optional.empty();
            holdStartMs = 0L;
        }
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        Entity candidate = confirmedClaimCandidate();
        if (candidate != null) {
            int xpCost = Config.BOND_XP_LEVEL_COST.get();
            if (xpCost > 0) renderXpCostLine(graphics, font, xpCost);

            int currentButtonY = buttonY();
            boolean hover = inBox(mouseX, mouseY, buttonX, currentButtonY, buttonW, BUTTON_HEIGHT);
            String entityTypePath = BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType()).getPath();
            Component label = Component.translatable("kindred.screen.bind", entityTypePath);
            drawButton(graphics, font, buttonX, currentButtonY, buttonW, BUTTON_HEIGHT, label,
                    hover ? C_BTN_CLAIM_HOVER : C_BTN_CLAIM,
                    holdProgress());
            return;
        }
        renderDenyOrHint(graphics, font);
    }

    private void renderXpCostLine(GuiGraphics graphics, Font font, int xpCost) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean canAfford = player == null
                || player.getAbilities().instabuild
                || player.experienceLevel >= xpCost;
        int color = canAfford ? C_TEXT_MUTED : C_XP_COST_UNAFFORDABLE;
        Component label = Component.translatable("kindred.bind.cost", xpCost);
        graphics.drawCenteredString(font, label, buttonX + buttonW / 2, costLineY, color);
    }

    private void renderDenyOrHint(GuiGraphics graphics, Font font) {
        Optional<String> effectiveDenyKey = serverDenyKey.isPresent()
                ? serverDenyKey
                : (isAtCapacity() ? Optional.of(capacityDenyKey()) : Optional.empty());
        Component message = effectiveDenyKey
                .map(this::resolveDenyMessage)
                .orElse(Component.translatable("kindred.screen.bind_hint"));
        graphics.drawCenteredString(font, message,
                buttonX + buttonW / 2,
                buttonAnchorY + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1,
                C_TEXT_MUTED);
    }

    private Component resolveDenyMessage(String key) {
        return switch (key) {
            case "kindred.bind.deny.not_enough_xp" ->
                    Component.translatable(key, Config.BOND_XP_LEVEL_COST.get());
            case "kindred.bind.deny.pmmo_locked" -> Component.translatable(
                    key,
                    Component.translatable("pmmo." + Config.PMMO_SKILL.get()),
                    Config.PMMO_START_LEVEL.get());
            case "kindred.bind.deny.at_capacity" -> resolveAtCapacityMessage(key);
            default -> Component.translatable(key);
        };
    }

    private Component resolveAtCapacityMessage(String fallbackKey) {
        int currentBondCount = ClientRosterData.bonds().size();
        boolean linearGrowth = Config.PMMO_ENABLED.get()
                && Config.PMMO_MODE.get() == PmmoMode.LINEAR
                && currentBondCount < Config.MAX_BONDS.get();
        if (linearGrowth) {
            int nextUnlockLevel = Config.PMMO_START_LEVEL.get()
                    + currentBondCount * Config.PMMO_INCREMENT_PER_BOND.get();
            return Component.translatable("kindred.bind.deny.pmmo_next_unlock",
                    Component.translatable("pmmo." + Config.PMMO_SKILL.get()),
                    nextUnlockLevel);
        }
        return Component.translatable(fallbackKey);
    }

    private int buttonY() {
        if (Config.BOND_XP_LEVEL_COST.get() > 0) {
            return costLineY + Minecraft.getInstance().font.lineHeight + 1;
        }
        return buttonAnchorY;
    }

    private float holdProgress() {
        if (holdStartMs == 0L) return 0F;
        return Math.min(1F, (System.currentTimeMillis() - holdStartMs) / (float) BIND_HOLD_MS);
    }

    private Entity confirmedClaimCandidate() {
        if (raycastCandidate != null && raycastCandidate.isRemoved()) {
            raycastCandidate = null;
            serverBindEligibility = null;
        }
        return Boolean.TRUE.equals(serverBindEligibility) ? raycastCandidate : null;
    }

    private static Entity raycastForBindCandidate(LocalPlayer player) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 lookVector = player.getViewVector(1.0F);
        Vec3 reachEnd = eyePosition.add(lookVector.scale(CLAIM_RAYCAST_DISTANCE));
        AABB searchBox = player.getBoundingBox()
                .expandTowards(lookVector.scale(CLAIM_RAYCAST_DISTANCE))
                .inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eyePosition, reachEnd, searchBox,
                entity -> !entity.isSpectator() && entity.isPickable(),
                CLAIM_RAYCAST_DISTANCE * CLAIM_RAYCAST_DISTANCE);
        return hit != null ? hit.getEntity() : null;
    }

    private static boolean passesClientGates(Entity entity) {
        if (!(entity instanceof OwnableEntity)) return false;
        if (Config.REQUIRE_SADDLEABLE.get() && !(entity instanceof Saddleable)) return false;
        return true;
    }

    private static boolean isAtCapacity() {
        int cap = ClientRosterData.effectiveMaxBonds();
        return cap == 0 || ClientRosterData.bonds().size() >= cap;
    }

    private static String capacityDenyKey() {
        return ClientRosterData.effectiveMaxBonds() == 0
                ? "kindred.bind.deny.pmmo_locked"
                : "kindred.bind.deny.at_capacity";
    }
}
