package net.silvertide.kindred.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.silvertide.kindred.bond.HoldManager;
import net.silvertide.kindred.client.data.ClientRosterData;
import net.silvertide.kindred.client.data.HoldActionState;
import net.silvertide.kindred.client.data.PreviewEntityCache;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.network.BondView;
import net.silvertide.kindred.network.packet.C2SCancelHold;
import net.silvertide.kindred.network.packet.C2SOpenRoster;
import net.silvertide.kindred.network.packet.C2SRequestHold;

import java.util.List;
import java.util.UUID;

import static net.silvertide.kindred.client.screen.ScreenDrawUtil.dimensionName;
import static net.silvertide.kindred.client.screen.ScreenDrawUtil.drawBorder;
import static net.silvertide.kindred.client.screen.ScreenDrawUtil.drawButton;
import static net.silvertide.kindred.client.screen.ScreenDrawUtil.drawRadialSweep;
import static net.silvertide.kindred.client.screen.ScreenDrawUtil.drawStar;
import static net.silvertide.kindred.client.screen.ScreenDrawUtil.entityTypeName;
import static net.silvertide.kindred.client.screen.ScreenDrawUtil.formatDurationCoarse;
import static net.silvertide.kindred.client.screen.ScreenDrawUtil.inBox;

public final class RosterScreen extends Screen {
    private static final int PANEL_WIDTH = 400;
    private static final int ROW_W = 280;
    private static final int ROW_HEIGHT = 32;
    private static final int ROW_PAD = 4;

    private static final int ROW_NAME_Y_OFFSET = 5;
    private static final int ROW_SUBTITLE_Y_OFFSET = 19;

    private static final int ROW_BTN_H = 14;
    private static final int ROW_SUMMON_W = 48;
    private static final int ROW_DISMISS_W = 48;
    private static final int ROW_BREAK_W = 14;
    private static final int ROW_BTN_GAP = 4;
    private static final int FOOTER_H = 32;
    private static final int CLAIM_BTN_H = 20;
    private static final long BREAK_CONFIRM_TTL_MS = 3000L;

    private static final int C_BG = 0xCC101418;
    private static final int C_BORDER = 0xFF4A5568;
    private static final int C_SEPARATOR = 0xFF2A323C;
    private static final int C_ROW_BG = 0xFF1B2128;
    private static final int C_ROW_HOVER = 0xFF263039;
    private static final int C_ROW_SELECTED = 0xFF2D3947;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_TEXT_MUTED = 0xFF8FA0B0;
    private static final int C_BTN_SUMMON = 0xFF3A7F5A;
    private static final int C_BTN_SUMMON_HOVER = 0xFF4FA374;
    private static final int C_BTN_SUMMON_DISABLED = 0xFF22302A;
    private static final int C_BTN_BREAK = 0xFF7A3A3A;
    private static final int C_BTN_BREAK_HOVER = 0xFF994A4A;
    private static final int C_BTN_BREAK_CONFIRM = 0xFFD45A5A;
    private static final int C_BTN_BREAK_DISABLED = 0xFF302222;
    private static final int C_BTN_DISMISS = 0xFF6A5A3A;
    private static final int C_BTN_DISMISS_HOVER = 0xFF8A7A52;
    private static final int C_BTN_DISMISS_DISABLED = 0xFF2A2620;
    private static final int C_BTN_TEXT_DISABLED = 0xFF6F6A60;
    private static final int C_STAR_ACTIVE = 0xFFE7B43B;
    private static final int C_RENAME_EDIT_TEXT = 0xFFE7B43B;
    private static final int C_STAR_INACTIVE = 0xFF4A5260;
    private static final int C_PIE_FILL = 0xFFB0C4D8;
    private static final int C_PIE_RADIUS = 5;

    private static final int STAR_COL_W = 16;

    private int leftPos;
    private int topPos;
    private int panelHeight;
    private int rowsTop;
    private int rowsBottom;
    private int separatorX;
    private int claimBtnX;
    private int claimBtnY;
    private int claimBtnW;
    private int scrollOffset = 0;

    private UUID breakArmedBondId = null;
    private long breakArmedExpiresAt = 0L;

    private RowHold rowHold = null;

    private enum RowHoldAction { SUMMON, DISMISS, BREAK }

    private record RowHold(UUID bondId, RowHoldAction action) {}

    private final RenameEditor renameEditor = new RenameEditor();
    private final BindFooter bindFooter = new BindFooter();
    private final PreviewPane previewPane = new PreviewPane(renameEditor);

    public RosterScreen() {
        super(Component.translatable("kindred.screen.title"));
    }

    @Override
    protected void init() {
        super.init();
        panelHeight = Math.min(this.height - 40, 6 * ROW_HEIGHT + 24 + FOOTER_H);
        leftPos = (this.width - PANEL_WIDTH) / 2;
        topPos = (this.height - panelHeight) / 2;
        rowsTop = topPos + 24;
        rowsBottom = topPos + panelHeight - FOOTER_H;

        separatorX = leftPos + ROW_PAD + ROW_W + 4;
        int previewX = separatorX + 4;

        claimBtnW = ROW_W;
        claimBtnX = leftPos + ROW_PAD;
        claimBtnY = topPos + panelHeight - FOOTER_H + (FOOTER_H - CLAIM_BTN_H) / 2;

        previewPane.configureLayout(previewX, rowsTop, rowsBottom);
        previewPane.selectActiveOnOpen();

        int costLineY = topPos + panelHeight - FOOTER_H + 1;
        bindFooter.configureLayout(claimBtnX, claimBtnY, claimBtnW, costLineY);
        bindFooter.onOpen(Minecraft.getInstance().player);

        PacketDistributor.sendToServer(new C2SOpenRoster());
    }

    public void onBindCandidateResult(java.util.UUID entityUUID, boolean canBind, java.util.Optional<String> denyKey) {
        bindFooter.onCandidateResult(entityUUID, canBind, denyKey);
    }

    @Override
    public void removed() {
        super.removed();
        PreviewEntityCache.clear();
        cancelRowHoldAndNotifyServer();
        bindFooter.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        processRowHold(mouseX, mouseY);
        bindFooter.tickHold(mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        g.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + panelHeight, C_BG);
        drawBorder(g, leftPos, topPos, PANEL_WIDTH, panelHeight, C_BORDER);

        g.drawCenteredString(font, getTitle(), leftPos + PANEL_WIDTH / 2, topPos + 8, C_TEXT);

        int bondCount = ClientRosterData.bonds().size();
        int maxBonds = ClientRosterData.effectiveMaxBonds();
        g.drawString(font, bondCount + "/" + maxBonds, leftPos + 6, topPos + 8, C_TEXT_MUTED);

        if (ClientRosterData.isGlobalSummonOnCooldown()) {
            long remainingMs = ClientRosterData.globalCooldownRemainingMsNow();
            Component text = Component.translatable("kindred.screen.summon_cooldown",
                    formatDurationCoarse(remainingMs));
            int tw = font.width(text);
            g.drawString(font, text, leftPos + PANEL_WIDTH - 6 - tw, topPos + 8, C_TEXT_MUTED);
        }

        g.fill(separatorX, rowsTop, separatorX + 1, rowsBottom, C_SEPARATOR);

        previewPane.refreshSelection();

        List<BondView> bonds = ClientRosterData.bonds();
        if (bonds.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("kindred.screen.empty"),
                    leftPos + ROW_PAD + ROW_W / 2, (rowsTop + rowsBottom) / 2 - 4, C_TEXT_MUTED);
        } else {
            int rowsLeft = leftPos + ROW_PAD;
            g.enableScissor(rowsLeft, rowsTop, rowsLeft + ROW_W, rowsBottom);
            for (int i = 0; i < bonds.size(); i++) {
                int rowY = rowsTop + (i - scrollOffset) * ROW_HEIGHT;
                if (rowY + ROW_HEIGHT < rowsTop || rowY > rowsBottom) continue;
                renderRow(g, bonds.get(i), rowsLeft, rowY, ROW_W, mouseX, mouseY);
            }
            g.disableScissor();
        }

        previewPane.render(g, font, mouseX, mouseY);
        bindFooter.render(g, font, mouseX, mouseY);
    }

    private void processRowHold(int mouseX, int mouseY) {
        if (rowHold == null) return;

        List<BondView> bonds = ClientRosterData.bonds();
        int rowIndex = -1;
        for (int i = 0; i < bonds.size(); i++) {
            if (bonds.get(i).bondId().equals(rowHold.bondId())) {
                rowIndex = i;
                break;
            }
        }
        if (rowIndex < 0) {
            cancelRowHoldAndNotifyServer();
            return;
        }

        int rowY = rowsTop + (rowIndex - scrollOffset) * ROW_HEIGHT;
        if (rowY + ROW_HEIGHT - 2 <= rowsTop || rowY >= rowsBottom) {
            cancelRowHoldAndNotifyServer();
            return;
        }

        int rowLeftX = leftPos + ROW_PAD;
        int buttonHeight = ROW_HEIGHT - 10;
        int buttonY = rowY + 4;
        boolean dismissEnabled = Config.ALLOW_DISMISSING.get();
        int summonButtonWidth = 50;
        int dismissButtonWidth = 50;
        int breakSmallButtonWidth = 16;
        int rowRightEdge = rowLeftX + ROW_W - 4;
        int breakSmallButtonX = rowRightEdge - breakSmallButtonWidth;
        int dismissButtonX = breakSmallButtonX - dismissButtonWidth - 4;
        int summonButtonX = (dismissEnabled ? dismissButtonX : breakSmallButtonX) - summonButtonWidth - 4;
        int confirmButtonX = dismissEnabled ? dismissButtonX : summonButtonX;

        int heldButtonX;
        int heldButtonWidth;
        if (rowHold.action() == RowHoldAction.SUMMON) {
            heldButtonX = summonButtonX;
            heldButtonWidth = summonButtonWidth;
        } else if (rowHold.action() == RowHoldAction.DISMISS) {
            heldButtonX = dismissButtonX;
            heldButtonWidth = dismissButtonWidth;
        } else {
            heldButtonX = confirmButtonX;
            heldButtonWidth = rowRightEdge - confirmButtonX;
        }

        if (!inBox(mouseX, mouseY, heldButtonX, buttonY, heldButtonWidth, buttonHeight)) {
            cancelRowHoldAndNotifyServer();
        }
    }

    private void renderRow(GuiGraphics g, BondView bond, int x, int y, int w, int mx, int my) {
        int rowH = ROW_HEIGHT - 2;
        boolean rowHover = mx >= x && mx < x + w && my >= y && my < y + rowH;
        boolean selected = previewPane.isSelected(bond.bondId());
        int rowBg = selected ? C_ROW_SELECTED : (rowHover ? C_ROW_HOVER : C_ROW_BG);
        g.fill(x, y, x + w, y + rowH, rowBg);

        int starCx = x + STAR_COL_W / 2;
        int starCy = y + rowH / 2;
        int starColor = bond.isActive() ? C_STAR_ACTIVE : C_STAR_INACTIVE;
        drawStar(g, starCx, starCy, starColor);

        int textX = x + STAR_COL_W + 4;
        String name;
        int nameColor = C_TEXT;
        if (renameEditor.isEditingBond(bond.bondId())) {
            boolean caretVisible = (System.currentTimeMillis() / 500L) % 2L == 0L;
            name = renameEditor.editBuffer() + (caretVisible ? "_" : " ");
            nameColor = C_RENAME_EDIT_TEXT;
        } else {
            name = bond.displayName().orElseGet(() -> entityTypeName(bond).getString());
        }
        g.drawString(font, name, textX, y + ROW_NAME_Y_OFFSET, nameColor);

        Component stateOrDim;
        if (ClientRosterData.isRevivalPending(bond)) {
            stateOrDim = Component.translatable("kindred.screen.state_limbo");
        } else if (bond.loaded()) {
            stateOrDim = dimensionName(bond.lastSeenDim());
        } else {
            stateOrDim = Component.translatable("kindred.screen.state_resting");
        }
        Component subtitle = entityTypeName(bond).copy().append(" · ").append(stateOrDim);
        g.drawString(font, subtitle, textX, y + ROW_SUBTITLE_Y_OFFSET, C_TEXT_MUTED);

        int btnH = ROW_BTN_H;
        int btnY = y + 2;
        int rightEdge = x + w - 4;
        boolean dismissEnabled = Config.ALLOW_DISMISSING.get();
        int breakSmallX = rightEdge - ROW_BREAK_W;
        int dismissX = breakSmallX - ROW_BTN_GAP - ROW_DISMISS_W;
        int summonX = (dismissEnabled ? dismissX : breakSmallX) - ROW_BTN_GAP - ROW_SUMMON_W;
        int summonW = ROW_SUMMON_W;
        int dismissW = ROW_DISMISS_W;
        int breakSmallW = ROW_BREAK_W;

        boolean breakHoldActive = rowHold != null
                && rowHold.bondId().equals(bond.bondId())
                && rowHold.action() == RowHoldAction.BREAK;
        boolean armed = bond.bondId().equals(breakArmedBondId)
                && (System.currentTimeMillis() < breakArmedExpiresAt || breakHoldActive);

        int confirmX = dismissEnabled ? dismissX : summonX;
        int confirmW = rightEdge - confirmX;
        boolean summonHiddenByConfirm = armed && !dismissEnabled;

        float summonHoldProgress = 0F;
        float dismissHoldProgress = 0F;
        float breakHoldProgress = 0F;
        if (HoldActionState.isActive() && bond.bondId().equals(HoldActionState.bondId())) {
            float progress = HoldActionState.progress();
            switch (HoldActionState.action()) {
                case SUMMON_BOND -> summonHoldProgress = progress;
                case DISMISS -> dismissHoldProgress = progress;
                case BREAK -> breakHoldProgress = progress;
                case SUMMON_KEYBIND -> { }
            }
        }

        if (!summonHiddenByConfirm) {
            boolean summonDisabled = ClientRosterData.isGlobalSummonOnCooldown()
                    || ClientRosterData.isOnCooldown(bond)
                    || ClientRosterData.isRevivalPending(bond);
            boolean summonHover = !summonDisabled && inBox(mx, my, summonX, btnY, summonW, btnH);

            int summonColor = summonDisabled
                    ? C_BTN_SUMMON_DISABLED
                    : (summonHover ? C_BTN_SUMMON_HOVER : C_BTN_SUMMON);
            long sweepRemainingMs = 0L;
            long sweepTotalMs = 0L;
            Component tooltipText = null;
            Component summonLabel;
            if (ClientRosterData.isRevivalPending(bond)) {
                sweepRemainingMs = ClientRosterData.revivalRemainingMsNow(bond);
                sweepTotalMs = Config.revivalCooldownMs();
                tooltipText = Component.translatable("kindred.screen.respawning",
                        formatDurationCoarse(sweepRemainingMs));
                summonLabel = Component.empty();
            } else if (summonDisabled) {
                long perBondRemaining = ClientRosterData.bondCooldownRemainingMsNow(bond);
                long perBondTotal = Config.summonCooldownMs();
                long globalRemaining = ClientRosterData.globalCooldownRemainingMsNow();
                long globalTotal = Config.summonGlobalCooldownMs();
                if (perBondRemaining >= globalRemaining) {
                    sweepRemainingMs = perBondRemaining;
                    sweepTotalMs = perBondTotal;
                } else {
                    sweepRemainingMs = globalRemaining;
                    sweepTotalMs = globalTotal;
                }
                tooltipText = Component.literal(formatDurationCoarse(sweepRemainingMs));
                summonLabel = Component.empty();
            } else {
                summonLabel = Component.translatable("kindred.screen.summon");
            }
            int summonTextColor = summonDisabled ? C_BTN_TEXT_DISABLED : C_TEXT;
            drawButton(g, font, summonX, btnY, summonW, btnH,
                    summonLabel,
                    summonColor,
                    summonHoldProgress,
                    summonTextColor);
            if (sweepTotalMs > 0L && sweepRemainingMs > 0L) {
                float progress = Math.min(1F, sweepRemainingMs / (float) sweepTotalMs);
                int pieCx = summonX + summonW / 2;
                int pieCy = btnY + btnH / 2;
                drawRadialSweep(g, pieCx, pieCy, C_PIE_RADIUS, progress, C_PIE_FILL);
                if (tooltipText != null && inBox(mx, my, summonX, btnY, summonW, btnH)) {
                    setTooltipForNextRenderPass(tooltipText);
                }
            }
        }

        if (armed) {
            boolean confirmHover = inBox(mx, my, confirmX, btnY, confirmW, btnH);
            drawButton(g, font, confirmX, btnY, confirmW, btnH,
                    Component.translatable("kindred.screen.break_confirm"),
                    confirmHover ? C_BTN_BREAK_CONFIRM : C_BTN_BREAK_HOVER,
                    breakHoldProgress);
        } else {
            if (dismissEnabled) {
                boolean dismissDisabled = ClientRosterData.isRevivalPending(bond) || !bond.loaded();
                boolean dismissHover = !dismissDisabled && inBox(mx, my, dismissX, btnY, dismissW, btnH);
                int dismissColor = dismissDisabled
                        ? C_BTN_DISMISS_DISABLED
                        : (dismissHover ? C_BTN_DISMISS_HOVER : C_BTN_DISMISS);
                int dismissTextColor = dismissDisabled ? C_BTN_TEXT_DISABLED : C_TEXT;
                drawButton(g, font, dismissX, btnY, dismissW, btnH,
                        Component.translatable("kindred.screen.dismiss"),
                        dismissColor,
                        dismissHoldProgress,
                        dismissTextColor);
            }

            boolean breakDisabled = ClientRosterData.isRevivalPending(bond);
            boolean breakHover = !breakDisabled && inBox(mx, my, breakSmallX, btnY, breakSmallW, btnH);
            int breakColor = breakDisabled
                    ? C_BTN_BREAK_DISABLED
                    : (breakHover ? C_BTN_BREAK_HOVER : C_BTN_BREAK);
            int breakTextColor = breakDisabled ? C_BTN_TEXT_DISABLED : C_TEXT;
            drawButton(g, font, breakSmallX, btnY, breakSmallW, btnH,
                    Component.literal("X"),
                    breakColor,
                    0F,
                    breakTextColor);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mxAll = (int) mouseX;
        int myAll = (int) mouseY;

        if (bindFooter.tryHandleClick(mxAll, myAll)) {
            return true;
        }

        if (previewPane.tryHandleClick(mxAll, myAll)) {
            return true;
        }

        renameEditor.commitEditing();

        List<BondView> bonds = ClientRosterData.bonds();
        for (int i = 0; i < bonds.size(); i++) {
            int rowY = rowsTop + (i - scrollOffset) * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < rowsTop || rowY > rowsBottom) continue;

            int x = leftPos + ROW_PAD;
            int rowH = ROW_HEIGHT - 2;
            int btnH = ROW_BTN_H;
            int btnY = rowY + 2;
            int summonW = ROW_SUMMON_W;
            int dismissW = ROW_DISMISS_W;
            int breakSmallW = ROW_BREAK_W;
            int rightEdge = x + ROW_W - 4;
            boolean dismissEnabled = Config.ALLOW_DISMISSING.get();
            int breakSmallX = rightEdge - breakSmallW;
            int dismissX = breakSmallX - ROW_BTN_GAP - dismissW;
            int summonX = (dismissEnabled ? dismissX : breakSmallX) - ROW_BTN_GAP - summonW;

            BondView bond = bonds.get(i);
            int mx = (int) mouseX;
            int my = (int) mouseY;
            long now = System.currentTimeMillis();
            boolean armed = bond.bondId().equals(breakArmedBondId) && now < breakArmedExpiresAt;
            boolean summonHiddenByConfirm = armed && !dismissEnabled;

            if (my < rowY || my >= rowY + rowH) continue;
            if (mx < x || mx >= x + ROW_W) continue;

            if (!summonHiddenByConfirm && inBox(mx, my, summonX, btnY, summonW, btnH)) {
                rowHold = new RowHold(bond.bondId(), RowHoldAction.SUMMON);
                PacketDistributor.sendToServer(new C2SRequestHold(
                        HoldManager.Action.SUMMON_BOND, java.util.Optional.of(bond.bondId())));
                return true;
            }

            if (armed) {
                int confirmX = dismissEnabled ? dismissX : summonX;
                int confirmW = rightEdge - confirmX;
                if (inBox(mx, my, confirmX, btnY, confirmW, btnH)) {
                    rowHold = new RowHold(bond.bondId(), RowHoldAction.BREAK);
                    PacketDistributor.sendToServer(new C2SRequestHold(
                            HoldManager.Action.BREAK, java.util.Optional.of(bond.bondId())));
                    return true;
                }
            } else {
                if (dismissEnabled && inBox(mx, my, dismissX, btnY, dismissW, btnH)) {
                    rowHold = new RowHold(bond.bondId(), RowHoldAction.DISMISS);
                    PacketDistributor.sendToServer(new C2SRequestHold(
                            HoldManager.Action.DISMISS, java.util.Optional.of(bond.bondId())));
                    return true;
                }
                if (inBox(mx, my, breakSmallX, btnY, breakSmallW, btnH)) {
                    if (ClientRosterData.isRevivalPending(bond)) return true;
                    breakArmedBondId = bond.bondId();
                    breakArmedExpiresAt = now + BREAK_CONFIRM_TTL_MS;
                    return true;
                }
            }

            previewPane.setSelected(bond.bondId());
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (renameEditor.tryHandleCharTyped(codePoint)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renameEditor.tryHandleKeyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (rowHold != null) cancelRowHoldAndNotifyServer();
            bindFooter.onMouseReleased();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void cancelRowHold() {
        rowHold = null;
    }

    private void cancelRowHoldAndNotifyServer() {
        if (rowHold == null) return;
        rowHold = null;
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new C2SCancelHold());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int n = ClientRosterData.bonds().size();
        int visibleRows = (rowsBottom - rowsTop) / ROW_HEIGHT;
        int maxOffset = Math.max(0, n - visibleRows);
        if (scrollY > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        else if (scrollY < 0) scrollOffset = Math.min(maxOffset, scrollOffset + 1);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
