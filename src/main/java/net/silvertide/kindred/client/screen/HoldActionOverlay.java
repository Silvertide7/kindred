package net.silvertide.kindred.client.screen;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.silvertide.kindred.bond.HoldManager;
import net.silvertide.kindred.client.data.HoldActionState;
import net.silvertide.kindred.config.ClientConfig;

public final class HoldActionOverlay {
    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 6;

    private static final int C_BAR_BORDER = 0xCC000000;
    private static final int C_BAR_TRACK = 0xFF333333;
    private static final int C_BAR_FILL_INTERRUPT = 0xFFE7B43B;
    private static final int C_BAR_FILL_SUMMON = 0xFF4FA374;
    private static final int C_LABEL_TEXT = 0xFFFFFFFF;

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!HoldActionState.isActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int offsetX = ClientConfig.HOLD_BAR_OFFSET_X.get();
        int offsetY = ClientConfig.HOLD_BAR_OFFSET_Y.get();
        int barX = (screenWidth - BAR_WIDTH) / 2 + offsetX;
        int barY = screenHeight - 70 + offsetY;

        HoldManager.Action action = HoldActionState.action();
        Component label = switch (action) {
            case DISMISS -> Component.translatable("kindred.hud.dismissing");
            case BREAK -> Component.translatable("kindred.hud.breaking");
            case SUMMON_KEYBIND, SUMMON_BOND -> Component.translatable("kindred.hud.summoning");
        };
        graphics.drawCenteredString(minecraft.font, label, screenWidth / 2 + offsetX, barY - 12, C_LABEL_TEXT);

        graphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, C_BAR_BORDER);
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, C_BAR_TRACK);

        int fillWidth = (int) (BAR_WIDTH * HoldActionState.progress());
        boolean isInterruptColor = action == HoldManager.Action.DISMISS
                || action == HoldManager.Action.BREAK;
        int fillColor = isInterruptColor ? C_BAR_FILL_INTERRUPT : C_BAR_FILL_SUMMON;
        graphics.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, fillColor);
    }

    private HoldActionOverlay() {}
}
