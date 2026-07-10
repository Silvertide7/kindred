package net.silvertide.kindred.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private ClientConfig() {}

    static { BUILDER.push("hud"); }

    public static final ModConfigSpec.IntValue HOLD_BAR_OFFSET_X = BUILDER
            .comment("Horizontal offset (pixels) of the summon/dismiss hold bar and its label. " +
                     "0 is centered; negative moves left, positive moves right.")
            .defineInRange("holdBarOffsetX", 0, -1000, 1000);

    public static final ModConfigSpec.IntValue HOLD_BAR_OFFSET_Y = BUILDER
            .comment("",
                     "Vertical offset (pixels) of the summon/dismiss hold bar and its label. " +
                     "0 sits just above the hotbar; negative moves up, positive moves down.")
            .defineInRange("holdBarOffsetY", 0, -1000, 1000);

    static { BUILDER.pop(); }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
