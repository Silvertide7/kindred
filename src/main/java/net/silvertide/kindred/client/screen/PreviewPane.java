package net.silvertide.kindred.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.silvertide.kindred.client.data.ClientRosterData;
import net.silvertide.kindred.client.data.PreviewEntityCache;
import net.silvertide.kindred.config.Config;
import net.silvertide.kindred.network.BondView;
import net.silvertide.kindred.network.packet.C2SReorderBond;
import net.silvertide.kindred.network.packet.C2SSetActivePet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.silvertide.kindred.client.screen.ScreenDrawUtil.drawButton;
import static net.silvertide.kindred.client.screen.ScreenDrawUtil.inBox;

public final class PreviewPane {
    private static final int PREVIEW_W = 100;
    private static final int PREVIEW_TOP_PAD = 16;
    private static final int PREVIEW_BTM_EXTEND = 14;
    private static final int PANE_BTN_PAD = 4;
    private static final int ACTION_BTN_H = 14;
    private static final int ACTION_BTN_GAP = 2;
    private static final float ENTITY_RENDER_SCALE_BY_PANE_HEIGHT = 0.5F;
    private static final float ENTITY_RENDER_SCALE_BY_PANE_WIDTH = 0.7F;
    private static final int MIN_ENTITY_SCALE = 20;
    private static final int MAX_ENTITY_SCALE = 60;
    private static final int MOUSE_Y_PITCH_CLAMP_BAND = 8;
    private static final float ENTITY_RENDER_FORWARD_OFFSET = 0.0625F;

    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_TEXT_MUTED = 0xFF8FA0B0;
    private static final int C_BTN_TEXT_DISABLED = 0xFF6F6A60;
    private static final int C_BTN_CLAIM = 0xFF3D5C8A;
    private static final int C_BTN_CLAIM_HOVER = 0xFF5278B0;
    private static final int C_BTN_DISABLED_EDGE = 0xFF2A2620;
    private static final int C_STAR_ACTIVE = 0xFFE7B43B;

    private final RenameEditor renameEditor;

    private int previewX;
    private int rowsTop;
    private int rowsBottom;

    private UUID selectedBondId;

    public PreviewPane(RenameEditor renameEditor) {
        this.renameEditor = renameEditor;
    }

    public void configureLayout(int previewX, int rowsTop, int rowsBottom) {
        this.previewX = previewX;
        this.rowsTop = rowsTop;
        this.rowsBottom = rowsBottom;
    }

    public void selectActiveOnOpen() {
        ClientRosterData.findActive().ifPresent(bv -> selectedBondId = bv.bondId());
    }

    public boolean isSelected(UUID bondId) {
        return selectedBondId != null && selectedBondId.equals(bondId);
    }

    public void setSelected(UUID bondId) {
        selectedBondId = bondId;
    }

    public void refreshSelection() {
        List<BondView> bonds = ClientRosterData.bonds();
        if (selectedBondId != null && bonds.stream().noneMatch(b -> b.bondId().equals(selectedBondId))) {
            selectedBondId = ClientRosterData.findActive()
                    .map(BondView::bondId)
                    .orElseGet(() -> bonds.isEmpty() ? null : bonds.get(0).bondId());
        }
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        BondView selected = currentSelection();
        if (selected == null) {
            graphics.drawCenteredString(font, Component.translatable("kindred.screen.preview_empty"),
                    previewX + PREVIEW_W / 2, (rowsTop + rowsBottom) / 2 - 4, C_TEXT_MUTED);
            return;
        }

        renderEntityOrPlaceholder(graphics, font, selected, mouseX, mouseY);
        renderPaneButtons(graphics, font, selected, mouseX, mouseY);
    }

    public boolean tryHandleClick(int mouseX, int mouseY) {
        BondView selected = currentSelection();
        if (selected == null) return false;

        int buttonsX = previewX + 4;
        int buttonsW = PREVIEW_W - 8;
        int moveHalfW = (buttonsW - ACTION_BTN_GAP) / 2;
        int moveDownX = buttonsX + moveHalfW + ACTION_BTN_GAP;
        int moveDownW = buttonsW - moveHalfW - ACTION_BTN_GAP;

        int moveY = moveButtonY();
        int renameY = renameButtonY();
        int setActiveY = setActiveButtonY();

        if (inBox(mouseX, mouseY, buttonsX, moveY, moveHalfW, ACTION_BTN_H)) {
            if (selectedBondIndex(selected) > 0) {
                renameEditor.commitEditing();
                PacketDistributor.sendToServer(new C2SReorderBond(selected.bondId(), -1));
            }
            return true;
        }
        if (inBox(mouseX, mouseY, moveDownX, moveY, moveDownW, ACTION_BTN_H)) {
            int idx = selectedBondIndex(selected);
            if (idx >= 0 && idx < ClientRosterData.bonds().size() - 1) {
                renameEditor.commitEditing();
                PacketDistributor.sendToServer(new C2SReorderBond(selected.bondId(), 1));
            }
            return true;
        }
        if (Config.ALLOW_RENAME.get()
                && inBox(mouseX, mouseY, buttonsX, renameY, buttonsW, ACTION_BTN_H)) {
            renameEditor.commitEditing();
            renameEditor.startEditing(selected);
            return true;
        }
        if (!selected.isActive()
                && inBox(mouseX, mouseY, buttonsX, setActiveY, buttonsW, ACTION_BTN_H)) {
            renameEditor.commitEditing();
            PacketDistributor.sendToServer(new C2SSetActivePet(Optional.of(selected.bondId())));
            return true;
        }
        return false;
    }

    private BondView currentSelection() {
        if (selectedBondId == null) return null;
        for (BondView bond : ClientRosterData.bonds()) {
            if (bond.bondId().equals(selectedBondId)) return bond;
        }
        return null;
    }

    private void renderEntityOrPlaceholder(GuiGraphics graphics, Font font, BondView selected,
                                           int mouseX, int mouseY) {
        int paneBottom = paneBottom();
        int entityRenderTop = rowsTop + PREVIEW_TOP_PAD;
        int entityRenderBottom = paneBottom - paneButtonAreaHeight();

        LivingEntity previewEntity = PreviewEntityCache.getOrBuild(selected);
        if (previewEntity == null) {
            graphics.drawCenteredString(font, Component.translatable("kindred.screen.preview_unavailable"),
                    previewX + PREVIEW_W / 2,
                    (entityRenderTop + entityRenderBottom) / 2 - 4,
                    C_TEXT_MUTED);
            return;
        }

        float boundingBoxWidth = Math.max(0.1F, previewEntity.getBbWidth());
        float boundingBoxHeight = Math.max(0.1F, previewEntity.getBbHeight());
        int paneHeight = entityRenderBottom - entityRenderTop;
        int scaleFromHeight = (int) (paneHeight * ENTITY_RENDER_SCALE_BY_PANE_HEIGHT / boundingBoxHeight);
        int scaleFromWidth = (int) (PREVIEW_W * ENTITY_RENDER_SCALE_BY_PANE_WIDTH / boundingBoxWidth);
        int scale = Math.max(MIN_ENTITY_SCALE, Math.min(MAX_ENTITY_SCALE, Math.min(scaleFromHeight, scaleFromWidth)));

        int verticalCenter = (entityRenderTop + entityRenderBottom) / 2;
        int clampedMouseY = Math.max(verticalCenter - MOUSE_Y_PITCH_CLAMP_BAND,
                Math.min(verticalCenter + MOUSE_Y_PITCH_CLAMP_BAND, mouseY));

        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                previewX, entityRenderTop,
                previewX + PREVIEW_W, entityRenderBottom,
                scale,
                ENTITY_RENDER_FORWARD_OFFSET,
                mouseX, clampedMouseY,
                previewEntity);
    }

    private void renderPaneButtons(GuiGraphics graphics, Font font, BondView selected,
                                   int mouseX, int mouseY) {
        boolean allowRename = Config.ALLOW_RENAME.get();
        int buttonsX = previewX + 4;
        int buttonsW = PREVIEW_W - 8;
        int setActiveY = setActiveButtonY();
        int renameY = renameButtonY();
        int moveY = moveButtonY();

        renderMoveButtons(graphics, font, selected, buttonsX, moveY, buttonsW, mouseX, mouseY);
        if (allowRename) {
            renderRenameButton(graphics, font, selected, buttonsX, renameY, buttonsW, mouseX, mouseY);
        }
        renderSetActiveButton(graphics, font, selected, buttonsX, setActiveY, buttonsW, mouseX, mouseY);
    }

    private void renderMoveButtons(GuiGraphics graphics, Font font, BondView selected,
                                   int buttonsX, int moveY, int buttonsW,
                                   int mouseX, int mouseY) {
        int moveHalfW = (buttonsW - ACTION_BTN_GAP) / 2;
        int moveUpX = buttonsX;
        int moveDownX = buttonsX + moveHalfW + ACTION_BTN_GAP;
        int moveDownW = buttonsW - moveHalfW - ACTION_BTN_GAP;

        int bondIdx = selectedBondIndex(selected);
        boolean canMoveUp = bondIdx > 0;
        boolean canMoveDown = bondIdx >= 0 && bondIdx < ClientRosterData.bonds().size() - 1;

        boolean moveUpHover = canMoveUp && inBox(mouseX, mouseY, moveUpX, moveY, moveHalfW, ACTION_BTN_H);
        boolean moveDownHover = canMoveDown && inBox(mouseX, mouseY, moveDownX, moveY, moveDownW, ACTION_BTN_H);

        int moveUpColor = !canMoveUp
                ? C_BTN_DISABLED_EDGE
                : (moveUpHover ? C_BTN_CLAIM_HOVER : C_BTN_CLAIM);
        int moveDownColor = !canMoveDown
                ? C_BTN_DISABLED_EDGE
                : (moveDownHover ? C_BTN_CLAIM_HOVER : C_BTN_CLAIM);

        drawButton(graphics, font, moveUpX, moveY, moveHalfW, ACTION_BTN_H,
                Component.translatable("kindred.screen.move_up"),
                moveUpColor, 0F, canMoveUp ? C_TEXT : C_BTN_TEXT_DISABLED);
        drawButton(graphics, font, moveDownX, moveY, moveDownW, ACTION_BTN_H,
                Component.translatable("kindred.screen.move_down"),
                moveDownColor, 0F, canMoveDown ? C_TEXT : C_BTN_TEXT_DISABLED);
    }

    private void renderRenameButton(GuiGraphics graphics, Font font, BondView selected,
                                    int buttonsX, int renameY, int buttonsW,
                                    int mouseX, int mouseY) {
        boolean editingThis = renameEditor.isEditingBond(selected.bondId());
        boolean renameHover = !editingThis
                && inBox(mouseX, mouseY, buttonsX, renameY, buttonsW, ACTION_BTN_H);
        int renameColor = editingThis
                ? C_BTN_CLAIM_HOVER
                : (renameHover ? C_BTN_CLAIM_HOVER : C_BTN_CLAIM);
        drawButton(graphics, font, buttonsX, renameY, buttonsW, ACTION_BTN_H,
                Component.translatable("kindred.screen.rename"), renameColor);
    }

    private void renderSetActiveButton(GuiGraphics graphics, Font font, BondView selected,
                                       int buttonsX, int setActiveY, int buttonsW,
                                       int mouseX, int mouseY) {
        boolean isAlreadyActive = selected.isActive();
        boolean setActiveHover = !isAlreadyActive
                && inBox(mouseX, mouseY, buttonsX, setActiveY, buttonsW, ACTION_BTN_H);
        Component label = isAlreadyActive
                ? Component.translatable("kindred.screen.is_active")
                : Component.translatable("kindred.screen.set_active");
        int color = isAlreadyActive
                ? C_STAR_ACTIVE
                : (setActiveHover ? C_BTN_CLAIM_HOVER : C_BTN_CLAIM);
        drawButton(graphics, font, buttonsX, setActiveY, buttonsW, ACTION_BTN_H, label, color);
    }

    private int paneBottom() {
        return rowsBottom + PREVIEW_BTM_EXTEND;
    }

    private int paneButtonAreaHeight() {
        return PANE_BTN_PAD
                + ACTION_BTN_H + ACTION_BTN_GAP
                + ACTION_BTN_H + ACTION_BTN_GAP
                + ACTION_BTN_H + PANE_BTN_PAD;
    }

    private int setActiveButtonY() {
        return paneBottom() - PANE_BTN_PAD - ACTION_BTN_H;
    }

    private int renameButtonY() {
        return setActiveButtonY() - ACTION_BTN_GAP - ACTION_BTN_H;
    }

    private int moveButtonY() {
        return Config.ALLOW_RENAME.get()
                ? renameButtonY() - ACTION_BTN_GAP - ACTION_BTN_H
                : renameButtonY();
    }

    private int selectedBondIndex(BondView selected) {
        List<BondView> bonds = ClientRosterData.bonds();
        for (int i = 0; i < bonds.size(); i++) {
            if (bonds.get(i).bondId().equals(selected.bondId())) return i;
        }
        return -1;
    }
}
