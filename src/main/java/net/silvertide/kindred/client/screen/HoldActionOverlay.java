package net.silvertide.kindred.client.screen;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.silvertide.kindred.bond.HoldManager;
import net.silvertide.kindred.client.data.HoldActionState;

/**
 * HUD overlay for keybind-initiated holds (DISMISS, SUMMON_KEYBIND).
 *
 * <p>Screen-row holds (SUMMON_BOND, BREAK, and DISMISS-from-screen) render their
 * own per-row progress fill inside the roster screen, so this overlay
 * short-circuits whenever any screen is open to avoid double-rendering
 * underneath with a generic label. (Vanilla calls {@code Gui.render} every
 * frame including during screens — the screen just draws on top of it — so we
 * have to opt out explicitly rather than rely on layering.)</p>
 *
 * <p>Registered as a gui layer via {@code RegisterGuiLayersEvent} in
 * {@code ClientGuiLayers}. No-op when {@link HoldActionState} is inactive.</p>
 */
public final class HoldActionOverlay {
    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 6;

    /** Semi-transparent black border around the bar — keeps the bar legible
     *  against bright skies / light terrain. */
    private static final int C_BAR_BORDER = 0xCC000000;
    /** Empty-bar track color. */
    private static final int C_BAR_TRACK = 0xFF333333;
    /** Gold/orange for "interrupting" actions (DISMISS, BREAK). */
    private static final int C_BAR_FILL_INTERRUPT = 0xFFE7B43B;
    /** Green for "constructive" actions (SUMMON). */
    private static final int C_BAR_FILL_SUMMON = 0xFF4FA374;
    private static final int C_LABEL_TEXT = 0xFFFFFFFF;

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!HoldActionState.isActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        // The roster screen renders its own per-row progress for screen-initiated
        // holds; we'd otherwise draw a redundant bar underneath with a label that
        // may be wrong for BREAK (mapping it to "Summoning…"). Bail when any
        // screen is open — keybind holds can't start while a screen is open
        // anyway (input capture handles that side).
        if (minecraft.screen != null) return;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        // Tucked above the hotbar / XP bar / health icons rather than mid-screen.
        // sh - 50 keeps the bar (and its label 12px above) clear of the survival
        // status bars at all GUI scales.
        int barX = (screenWidth - BAR_WIDTH) / 2;
        int barY = screenHeight - 50;

        HoldManager.Action action = HoldActionState.action();
        Component label = switch (action) {
            case DISMISS -> Component.translatable("kindred.hud.dismissing");
            case BREAK -> Component.translatable("kindred.hud.breaking");
            case SUMMON_KEYBIND, SUMMON_BOND -> Component.translatable("kindred.hud.summoning");
        };
        graphics.drawCenteredString(minecraft.font, label, screenWidth / 2, barY - 12, C_LABEL_TEXT);

        // Border + empty track.
        graphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, C_BAR_BORDER);
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, C_BAR_TRACK);

        // Fill width follows the server-driven progress fraction. Color picks
        // green for SUMMON and gold for the interrupting actions; BREAK gets
        // gold rather than its own red because it's screen-only and won't render
        // through this overlay in practice, but the value is correct if it ever does.
        int fillWidth = (int) (BAR_WIDTH * HoldActionState.progress());
        boolean isInterruptColor = action == HoldManager.Action.DISMISS
                || action == HoldManager.Action.BREAK;
        int fillColor = isInterruptColor ? C_BAR_FILL_INTERRUPT : C_BAR_FILL_SUMMON;
        graphics.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, fillColor);
    }

    private HoldActionOverlay() {}
}
