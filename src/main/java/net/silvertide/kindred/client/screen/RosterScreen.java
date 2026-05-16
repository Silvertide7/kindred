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

/**
 * Roster screen with two-column layout: row list on the left, entity preview pane on
 * the right. Clicking anywhere on a row body (not on a button/star) selects it,
 * driving which pet renders in the preview pane. Default selection on open is the
 * active pet.
 *
 * <p>Preview rendering goes through {@link InventoryScreen#renderEntityInInventoryFollowsMouse}
 * with {@link LivingEntity} instances built lazily by {@link PreviewEntityCache} from
 * each bond's snapshot NBT.</p>
 */
public final class RosterScreen extends Screen {
    private static final int PANEL_WIDTH = 400;
    private static final int ROW_W = 280;
    private static final int ROW_HEIGHT = 32;
    private static final int ROW_PAD = 4;

    // Row internal layout: name + buttons share the top line, type · dim sits below.
    private static final int ROW_NAME_Y_OFFSET = 5;
    private static final int ROW_SUBTITLE_Y_OFFSET = 19;

    // Row button layout — kept in one place so renderRow and mouseClicked stay in sync.
    private static final int ROW_BTN_H = 14;
    private static final int ROW_SUMMON_W = 48;
    private static final int ROW_DISMISS_W = 48;
    private static final int ROW_BREAK_W = 14;
    private static final int ROW_BTN_GAP = 4;
    private static final int FOOTER_H = 32;
    private static final int CLAIM_BTN_H = 20;
    private static final long BREAK_CONFIRM_TTL_MS = 3000L;

    // ARGB palette
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
    /** Dimmed label color for disabled buttons — pairs with the darker disabled
     *  background to make "this can't be clicked" obvious at a glance. */
    private static final int C_BTN_TEXT_DISABLED = 0xFF6F6A60;
    private static final int C_STAR_ACTIVE = 0xFFE7B43B;
    private static final int C_RENAME_EDIT_TEXT = 0xFFE7B43B;
    private static final int C_STAR_INACTIVE = 0xFF4A5260;
    /** Radial cooldown indicator: light wedge that drains counter-clockwise as
     *  the cooldown elapses. Drawn centered on the Summon button (replaces the
     *  text label entirely while the cooldown is running). */
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

    /** Tracks which row button the mouse is currently pressing. Null when no
     *  button is held.
     *
     *  <p>Purely client UX — progress and completion live entirely on the
     *  server (see {@link HoldActionState} for the server-pushed view). This
     *  field exists only so the screen can detect mouse-up, drag-off, and
     *  scrolled-out and send {@link C2SCancelHold} when any of those happen. */
    private RowHold rowHold = null;

    /** Which screen-row button initiated the hold. {@code BREAK} is shown via
     *  the Confirm button that replaces Dismiss+X after the small X is clicked. */
    private enum RowHoldAction { SUMMON, DISMISS, BREAK }

    /** Identifies a pressed row button by (bondId, action). No timing data —
     *  that's all server-side; the client only needs to know which button is
     *  being pressed for cancel-on-release / drag-off detection. */
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

    /**
     * Screen close hook. Runs on Esc, when replaced by another screen, and on
     * disconnect. We drop the entity preview cache and — if a row hold was
     * active when the screen closed — tell the server to cancel it so the
     * {@link net.silvertide.kindred.bond.HoldManager} entry doesn't outlive the
     * screen that owned it. The cancel helper internally null-checks the
     * connection, so the disconnect path is safe.
     */
    @Override
    public void removed() {
        super.removed();
        PreviewEntityCache.clear();
        cancelRowHoldAndNotifyServer();
        bindFooter.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Validate the active row-hold (if any) before drawing — cancels via
        // C2SCancelHold if the cursor drifted off, the row scrolled out, or the
        // bond was removed under us. Completion itself runs server-side; this
        // just keeps the local press state honest.
        processRowHold(mouseX, mouseY);
        bindFooter.tickHold(mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        g.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + panelHeight, C_BG);
        drawBorder(g, leftPos, topPos, PANEL_WIDTH, panelHeight, C_BORDER);

        g.drawCenteredString(font, getTitle(), leftPos + PANEL_WIDTH / 2, topPos + 8, C_TEXT);

        // Bond count (e.g. "3/10") — left-aligned in title bar, mirrors the cooldown
        // indicator on the right.
        int bondCount = ClientRosterData.bonds().size();
        int maxBonds = ClientRosterData.effectiveMaxBonds();
        g.drawString(font, bondCount + "/" + maxBonds, leftPos + 6, topPos + 8, C_TEXT_MUTED);

        // Global cooldown indicator (only when active). Right-aligned in title bar.
        if (ClientRosterData.isGlobalSummonOnCooldown()) {
            long remainingMs = ClientRosterData.globalCooldownRemainingMsNow();
            Component text = Component.translatable("kindred.screen.summon_cooldown",
                    formatDurationCoarse(remainingMs));
            int tw = font.width(text);
            g.drawString(font, text, leftPos + PANEL_WIDTH - 6 - tw, topPos + 8, C_TEXT_MUTED);
        }

        // Vertical separator between rows column and preview pane
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



    /**
     * Each render frame, validate that the active row-hold is still in a valid
     * state — row still exists, still on-screen, cursor still over the button.
     * If any of those fail, cancel the hold (locally and tell the server).
     *
     * <p>Note this method does NOT tick completion — server-side
     * {@link net.silvertide.kindred.bond.HoldManager#tickAll} owns that. The
     * screen only watches for client-side cancel conditions and reads
     * {@link HoldActionState#progress()} during render for the visual fill.</p>
     */
    private void processRowHold(int mouseX, int mouseY) {
        if (rowHold == null) return;

        // Locate the held row in the (possibly resorted) bond list. The list
        // can change between ticks via S2CRosterSync, so we re-resolve every
        // frame rather than caching an index.
        List<BondView> bonds = ClientRosterData.bonds();
        int rowIndex = -1;
        for (int i = 0; i < bonds.size(); i++) {
            if (bonds.get(i).bondId().equals(rowHold.bondId())) {
                rowIndex = i;
                break;
            }
        }
        if (rowIndex < 0) {
            // Bond gone from under us (broken, removed, etc.) — abort.
            cancelRowHoldAndNotifyServer();
            return;
        }

        int rowY = rowsTop + (rowIndex - scrollOffset) * ROW_HEIGHT;
        if (rowY + ROW_HEIGHT - 2 <= rowsTop || rowY >= rowsBottom) {
            // Row scrolled out of view — cancel so the player isn't holding an
            // invisible button.
            cancelRowHoldAndNotifyServer();
            return;
        }

        // Compute the button rect for the action being held. Layout mirrors
        // mouseClicked and renderRow so the drag-off check matches the actual
        // button geometry exactly.
        int rowLeftX = leftPos + ROW_PAD;
        int buttonHeight = ROW_HEIGHT - 10;
        int buttonY = rowY + 4;
        int summonButtonWidth = 50;
        int dismissButtonWidth = 50;
        int breakSmallButtonWidth = 16;
        int rowRightEdge = rowLeftX + ROW_W - 4;
        int breakSmallButtonX = rowRightEdge - breakSmallButtonWidth;
        int dismissButtonX = breakSmallButtonX - dismissButtonWidth - 4;
        int summonButtonX = dismissButtonX - summonButtonWidth - 4;

        int heldButtonX;
        int heldButtonWidth;
        if (rowHold.action() == RowHoldAction.SUMMON) {
            heldButtonX = summonButtonX;
            heldButtonWidth = summonButtonWidth;
        } else if (rowHold.action() == RowHoldAction.DISMISS) {
            heldButtonX = dismissButtonX;
            heldButtonWidth = dismissButtonWidth;
        } else {
            // BREAK: the Confirm button replaces both Dismiss and X while armed,
            // spanning from dismissButtonX out to the right edge.
            heldButtonX = dismissButtonX;
            heldButtonWidth = rowRightEdge - dismissButtonX;
        }

        if (!inBox(mouseX, mouseY, heldButtonX, buttonY, heldButtonWidth, buttonHeight)) {
            // Mouse drifted off the button. Mimics the keybind's "release means
            // cancel" contract — server validates the actual button geometry
            // didn't change, but for our purposes the cancel is unconditional.
            cancelRowHoldAndNotifyServer();
        }
    }

    private void renderRow(GuiGraphics g, BondView bond, int x, int y, int w, int mx, int my) {
        int rowH = ROW_HEIGHT - 2;
        boolean rowHover = mx >= x && mx < x + w && my >= y && my < y + rowH;
        boolean selected = previewPane.isSelected(bond.bondId());
        int rowBg = selected ? C_ROW_SELECTED : (rowHover ? C_ROW_HOVER : C_ROW_BG);
        g.fill(x, y, x + w, y + rowH, rowBg);

        // Diamond is a visual indicator only — clicking it does nothing now. Active is
        // set via the "Set Active" button under the preview pane to avoid mis-clicks.
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

        // Subtitle below the button line: "Horse · Overworld" when loaded,
        // "Horse · Limbo" while revival is pending (dead, respawn-locked),
        // "Horse · Resting" when otherwise dismissed/stored.
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
        // Buttons align with the top text line, not centered on the full row, so the
        // subtitle reads cleanly below them.
        int btnY = y + 2;
        int rightEdge = x + w - 4;
        int breakSmallX = rightEdge - ROW_BREAK_W;
        int dismissX = breakSmallX - ROW_BTN_GAP - ROW_DISMISS_W;
        int summonX = dismissX - ROW_BTN_GAP - ROW_SUMMON_W;
        int summonW = ROW_SUMMON_W;
        int dismissW = ROW_DISMISS_W;
        int breakSmallW = ROW_BREAK_W;

        // Armed state: TTL gate, OR an in-progress break-confirm hold for this bond
        // (so the Confirm button doesn't disappear mid-hold if the arm TTL expires).
        boolean breakHoldActive = rowHold != null
                && rowHold.bondId().equals(bond.bondId())
                && rowHold.action() == RowHoldAction.BREAK;
        boolean armed = bond.bondId().equals(breakArmedBondId)
                && (System.currentTimeMillis() < breakArmedExpiresAt || breakHoldActive);

        boolean summonDisabled = ClientRosterData.isGlobalSummonOnCooldown()
                || ClientRosterData.isOnCooldown(bond)
                || ClientRosterData.isRevivalPending(bond);
        boolean summonHover = !summonDisabled && inBox(mx, my, summonX, btnY, summonW, btnH);

        // Hold progress is server-driven: HoldActionState alone is the source
        // of truth. If a hold is active and targets this row's bond, map its
        // action onto the corresponding button's fill amount. SUMMON_KEYBIND
        // intentionally produces no row visual — it's drawn by HoldActionOverlay
        // on the HUD instead.
        float summonHoldProgress = 0F;
        float dismissHoldProgress = 0F;
        float breakHoldProgress = 0F;
        if (HoldActionState.isActive() && bond.bondId().equals(HoldActionState.bondId())) {
            float progress = HoldActionState.progress();
            switch (HoldActionState.action()) {
                case SUMMON_BOND -> summonHoldProgress = progress;
                case DISMISS -> dismissHoldProgress = progress;
                case BREAK -> breakHoldProgress = progress;
                case SUMMON_KEYBIND -> { /* keybind hold, rendered by HUD overlay */ }
            }
        }

        int summonColor = summonDisabled
                ? C_BTN_SUMMON_DISABLED
                : (summonHover ? C_BTN_SUMMON_HOVER : C_BTN_SUMMON);
        // Disabled-by-time states (revival, per-bond cooldown, global cooldown) all
        // share the same UI: no button label, radial sweep centered, precise time on
        // hover. Revival is checked first since "the pet is dead" outranks "rate-
        // limited"; otherwise we pick whichever cooldown has more time left so the
        // wedge matches the actual block.
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
            // Long cooldowns (20m+) make the wedge motion imperceptible, so surface
            // precise text on hover. Deferred via setTooltipForNextRenderPass so it
            // renders above the row scissor and any later panel content.
            if (tooltipText != null && inBox(mx, my, summonX, btnY, summonW, btnH)) {
                setTooltipForNextRenderPass(tooltipText);
            }
        }

        if (armed) {
            int confirmX = dismissX;
            int confirmW = rightEdge - confirmX;
            boolean confirmHover = inBox(mx, my, confirmX, btnY, confirmW, btnH);
            drawButton(g, font, confirmX, btnY, confirmW, btnH,
                    Component.translatable("kindred.screen.break_confirm"),
                    confirmHover ? C_BTN_BREAK_CONFIRM : C_BTN_BREAK_HOVER,
                    breakHoldProgress);
        } else {
            // Dismiss only makes sense for an entity that's actually in the world.
            // Revival-pending pets are dead; not-loaded pets are already stored.
            boolean dismissDisabled = ClientRosterData.isRevivalPending(bond) || !bond.loaded();
            boolean dismissHover = !dismissDisabled && inBox(mx, my, dismissX, btnY, dismissW, btnH);
            // Break is gated on revival too: the whole row quiets down together
            // while a dead pet is respawning, matching the server gate in
            // HoldEligibility.checkBreak.
            boolean breakDisabled = ClientRosterData.isRevivalPending(bond);
            boolean breakHover = !breakDisabled && inBox(mx, my, breakSmallX, btnY, breakSmallW, btnH);

            int dismissColor = dismissDisabled
                    ? C_BTN_DISMISS_DISABLED
                    : (dismissHover ? C_BTN_DISMISS_HOVER : C_BTN_DISMISS);
            int dismissTextColor = dismissDisabled ? C_BTN_TEXT_DISABLED : C_TEXT;
            drawButton(g, font, dismissX, btnY, dismissW, btnH,
                    Component.translatable("kindred.screen.dismiss"),
                    dismissColor,
                    dismissHoldProgress,
                    dismissTextColor);

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
            int breakSmallX = rightEdge - breakSmallW;
            int dismissX = breakSmallX - ROW_BTN_GAP - dismissW;
            int summonX = dismissX - ROW_BTN_GAP - summonW;

            BondView bond = bonds.get(i);
            int mx = (int) mouseX;
            int my = (int) mouseY;
            long now = System.currentTimeMillis();
            boolean armed = bond.bondId().equals(breakArmedBondId) && now < breakArmedExpiresAt;

            // Skip rows whose vertical area the click isn't on.
            if (my < rowY || my >= rowY + rowH) continue;
            // And whose horizontal area the click isn't on.
            if (mx < x || mx >= x + ROW_W) continue;

            if (inBox(mx, my, summonX, btnY, summonW, btnH)) {
                // Summon button clicked. Set the local press tracker so we can
                // detect release / drag-off / scroll-out, and ask the server to
                // start the hold. Server validates eligibility (cooldowns,
                // revival state, etc.) and either pushes S2CHoldStart or sends
                // a vanilla action-bar deny — no client pre-check.
                rowHold = new RowHold(bond.bondId(), RowHoldAction.SUMMON);
                PacketDistributor.sendToServer(new C2SRequestHold(
                        HoldManager.Action.SUMMON_BOND, java.util.Optional.of(bond.bondId())));
                return true;
            }

            if (armed) {
                int confirmX = dismissX;
                int confirmW = rightEdge - confirmX;
                if (inBox(mx, my, confirmX, btnY, confirmW, btnH)) {
                    // Break confirm button clicked (visible only after the X
                    // arms it). Same hold contract as summon/dismiss; release
                    // before completion cancels via mouseReleased.
                    rowHold = new RowHold(bond.bondId(), RowHoldAction.BREAK);
                    PacketDistributor.sendToServer(new C2SRequestHold(
                            HoldManager.Action.BREAK, java.util.Optional.of(bond.bondId())));
                    return true;
                }
            } else {
                if (inBox(mx, my, dismissX, btnY, dismissW, btnH)) {
                    // Dismiss button clicked. Server validates that the pet is
                    // loaded and within range — if not, the action-bar message
                    // tells the player why.
                    rowHold = new RowHold(bond.bondId(), RowHoldAction.DISMISS);
                    PacketDistributor.sendToServer(new C2SRequestHold(
                            HoldManager.Action.DISMISS, java.util.Optional.of(bond.bondId())));
                    return true;
                }
                if (inBox(mx, my, breakSmallX, btnY, breakSmallW, btnH)) {
                    // Don't arm the confirm flow during revival — the server gate
                    // would deny the eventual hold anyway, and arming would put
                    // the row into a confusing half-state with no path forward.
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

    /**
     * Left-mouse release → cancel any in-progress hold. Row holds notify the
     * server (it owns those); the Bind button hold is purely client-side and
     * just clears the timer.
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (rowHold != null) cancelRowHoldAndNotifyServer();
            bindFooter.onMouseReleased();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Called by {@link net.silvertide.kindred.client.network.ClientPacketHandler#onHoldStop}
     * when the server has cleared the hold (damage, death, completion, etc.).
     * Pure local cleanup — no packet sent, because the server already cancelled
     * and is notifying US.
     */
    public void cancelRowHold() {
        rowHold = null;
    }

    /**
     * Local clear plus {@link C2SCancelHold} to the server. Used when the
     * client originates the cancel — drag-off, scroll-out, mouse release,
     * screen close. The connection null-check guards against {@code removed()}
     * firing during a disconnect tear-down where there's no server to send to.
     */
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
