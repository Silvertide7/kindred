package net.silvertide.kindred.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private ClientConfig() {}

    static { BUILDER.push("hud"); }

    public static final ForgeConfigSpec.IntValue HOLD_BAR_OFFSET_X = BUILDER
            .comment("Horizontal offset (pixels) of the summon/dismiss hold bar and its label. " +
                     "0 is centered; negative moves left, positive moves right.")
            .defineInRange("holdBarOffsetX", 0, -1000, 1000);

    public static final ForgeConfigSpec.IntValue HOLD_BAR_OFFSET_Y = BUILDER
            .comment("",
                     "Vertical offset (pixels) of the summon/dismiss hold bar and its label. " +
                     "0 sits just above the hotbar; negative moves up, positive moves down.")
            .defineInRange("holdBarOffsetY", 0, -1000, 1000);

    static { BUILDER.pop(); }

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
