package net.silvertide.kindred.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.silvertide.kindred.config.ClientConfig;
import net.silvertide.kindred.network.BondView;

public final class ScreenDrawUtil {
    private ScreenDrawUtil() {}

    private static final int DEFAULT_BUTTON_TEXT_COLOR = 0xFFFFFFFF;
    private static final int BUTTON_HOLD_OVERLAY_COLOR = 0x66FFFFFF;
    private static final int BUTTON_TEXT_DISABLED_COLOR = 0xFF6F6A60;
    private static final int VANILLA_PANEL_COLOR = 0xFFC6C6C6;
    private static final int VANILLA_PANEL_LIGHT = 0xFFFFFFFF;
    private static final int VANILLA_PANEL_SHADOW = 0xFF555555;
    private static final int VANILLA_PANEL_OUTLINE = 0xFF000000;
    private static final int VANILLA_DISABLED_TEXT = 0xFFA0A0A0;
    private static final int VANILLA_ACCENT_ALPHA = 0x55000000;
    private static final int VANILLA_HOLD_OVERLAY_COLOR = 0x9955FF55;
    private static final int VANILLA_PANEL_TEXT_COLOR = 0xFF404040;
    private static final int VANILLA_PANEL_MUTED_TEXT_COLOR = 0xFF484848;
    private static final int CLASSIC_PANEL_TEXT_COLOR = 0xFFFFFFFF;
    private static final int CLASSIC_PANEL_MUTED_TEXT_COLOR = 0xFF8FA0B0;
    private static final WidgetSprites VANILLA_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("widget/button"),
            ResourceLocation.withDefaultNamespace("widget/button_disabled"),
            ResourceLocation.withDefaultNamespace("widget/button_highlighted"));

    public static boolean vanillaStyle() {
        return ClientConfig.USE_VANILLA_UI.get();
    }

    public static int panelTextColor() {
        return vanillaStyle() ? VANILLA_PANEL_TEXT_COLOR : CLASSIC_PANEL_TEXT_COLOR;
    }

    public static int panelMutedTextColor() {
        return vanillaStyle() ? VANILLA_PANEL_MUTED_TEXT_COLOR : CLASSIC_PANEL_MUTED_TEXT_COLOR;
    }

    public static void drawCenteredText(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    public static void drawVanillaPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, VANILLA_PANEL_OUTLINE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, VANILLA_PANEL_COLOR);
        graphics.fill(x + 1, y + 1, x + w - 2, y + 3, VANILLA_PANEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + h - 2, VANILLA_PANEL_LIGHT);
        graphics.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, VANILLA_PANEL_SHADOW);
        graphics.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, VANILLA_PANEL_SHADOW);
    }

    public record ButtonState(boolean enabled, boolean hovered, int accentColor) {
        public static ButtonState of(boolean enabled, boolean hovered) {
            return new ButtonState(enabled, enabled && hovered, 0);
        }

        public ButtonState withAccent(int accentColor) {
            return new ButtonState(enabled, hovered, accentColor);
        }
    }

    private static void drawVanillaButton(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                          Component label, float holdProgress, ButtonState state) {
        graphics.blitSprite(VANILLA_BUTTON_SPRITES.get(state.enabled(), state.hovered()), x, y, w, h);
        if (state.accentColor() != 0) {
            graphics.fill(x, y, x + w, y + h, (state.accentColor() & 0x00FFFFFF) | VANILLA_ACCENT_ALPHA);
        }
        if (holdProgress > 0F) {
            graphics.fill(x, y, x + Math.max(1, (int) (w * holdProgress)), y + h, VANILLA_HOLD_OVERLAY_COLOR);
        }
        drawCenteredText(graphics, font, label, x + w / 2, y + (h - font.lineHeight) / 2 + 1,
                state.enabled() ? DEFAULT_BUTTON_TEXT_COLOR : VANILLA_DISABLED_TEXT);
    }

    public static void drawButton(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                  Component label, int color, ButtonState state) {
        drawButton(graphics, font, x, y, w, h, label, color, 0F, state);
    }

    public static void drawButton(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                  Component label, int color, float holdProgress, ButtonState state) {
        if (vanillaStyle()) {
            drawVanillaButton(graphics, font, x, y, w, h, label, holdProgress, state);
            return;
        }
        graphics.fill(x, y, x + w, y + h, color);
        if (holdProgress > 0F) {
            int filledWidth = Math.max(1, (int) (w * holdProgress));
            graphics.fill(x, y, x + filledWidth, y + h, BUTTON_HOLD_OVERLAY_COLOR);
        }
        drawCenteredText(graphics, font, label, x + w / 2, y + (h - font.lineHeight) / 2 + 1,
                state.enabled() ? DEFAULT_BUTTON_TEXT_COLOR : BUTTON_TEXT_DISABLED_COLOR);
    }

    public static void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawRadialSweep(GuiGraphics graphics, int cx, int cy, int radius,
                                       float progress, int color) {
        int radiusSquared = radius * radius;
        double sweepRadians = progress * Math.PI * 2.0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy > radiusSquared) continue;
                double angleFromTopClockwise = Math.atan2(dx, -dy);
                if (angleFromTopClockwise < 0) angleFromTopClockwise += Math.PI * 2.0;
                if (angleFromTopClockwise > sweepRadians) continue;
                graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
        }
    }

    public static void drawStar(GuiGraphics graphics, int cx, int cy, int color) {
        graphics.fill(cx,     cy - 2, cx + 1, cy - 1, color);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy,     color);
        graphics.fill(cx - 2, cy,     cx + 3, cy + 1, color);
        graphics.fill(cx - 1, cy + 1, cx + 2, cy + 2, color);
        graphics.fill(cx,     cy + 2, cx + 1, cy + 3, color);
    }

    public static boolean inBox(int x, int y, int boxX, int boxY, int boxW, int boxH) {
        return x >= boxX && x < boxX + boxW && y >= boxY && y < boxY + boxH;
    }

    public static String formatDurationCoarse(long ms) {
        long totalSeconds = (ms + 999L) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    public static String prettifyResourcePath(String path) {
        if (path.isEmpty()) return path;
        StringBuilder builder = new StringBuilder(path.length());
        boolean nextCharUpper = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_') {
                builder.append(' ');
                nextCharUpper = true;
            } else if (nextCharUpper) {
                builder.append(Character.toUpperCase(c));
                nextCharUpper = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public static Component entityTypeName(BondView bond) {
        var type = BuiltInRegistries.ENTITY_TYPE.get(bond.entityType());
        return type != null ? type.getDescription() : Component.literal(bond.entityType().getPath());
    }

    public static Component dimensionName(ResourceLocation dim) {
        String translationKey = "kindred.dim." + dim.getNamespace() + "." + dim.getPath();
        return Component.translatableWithFallback(translationKey, prettifyResourcePath(dim.getPath()));
    }
}
