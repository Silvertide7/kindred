package net.silvertide.kindred.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ───── Bonding & roster ─────

    public static final ModConfigSpec.IntValue MAX_BONDS = BUILDER
            .comment("Maximum number of bonds per player.")
            .defineInRange("maxBonds", 10, 1, 64);

    public static final ModConfigSpec.BooleanValue REQUIRE_SADDLEABLE = BUILDER
            .comment("If true, only entities implementing Saddleable can be bonded (mount-only mode).")
            .define("requireSaddleable", false);

    // ───── Summoning behavior ─────

    public static final ModConfigSpec.DoubleValue WALK_RANGE = BUILDER
            .comment("If a summoned pet is within this distance (blocks) and in the same dimension, it walks instead of teleporting.")
            .defineInRange("walkRange", 30.0D, 0.0D, 256.0D);

    public static final ModConfigSpec.DoubleValue WALK_SPEED = BUILDER
            .comment("Pathfinding speed multiplier when walking to the player.")
            .defineInRange("walkSpeed", 1.8D, 0.1D, 8.0D);

    public static final ModConfigSpec.BooleanValue CROSS_DIM_ALLOWED = BUILDER
            .comment("Allow summoning a pet from another dimension.")
            .define("crossDimAllowed", true);

    public static final ModConfigSpec.BooleanValue REQUIRE_SPACE = BUILDER
            .comment("If true, refuse to summon when the 3x3x3 space around the player is obstructed.")
            .define("requireSpace", true);

    // ───── Cooldowns ─────

    public static final ModConfigSpec.IntValue SUMMON_COOLDOWN_TICKS = BUILDER
            .comment("Cooldown between summons of the same bond, in ticks (20 = 1 second).")
            .defineInRange("summonCooldownTicks", 100, 0, 72000);

    public static final ModConfigSpec.IntValue SUMMON_GLOBAL_COOLDOWN_SECONDS = BUILDER
            .comment("Per-player cooldown (in seconds) between any two summons regardless of which bond. " +
                     "0 disables. Distinct from summonCooldownTicks which only blocks summoning the same pet repeatedly.")
            .defineInRange("summonGlobalCooldownSeconds", 10, 0, 86400);

    // ───── Death & revival ─────

    public static final ModConfigSpec.BooleanValue DEATH_IS_PERMANENT = BUILDER
            .comment("If true, a bonded pet's death breaks the bond. If false, summoning a dead pet respawns it.")
            .define("deathIsPermanent", false);

    public static final ModConfigSpec.BooleanValue DROP_LOOT_ON_DEATH = BUILDER
            .comment("If true (vanilla), bonded pets drop their inventory and loot on death. " +
                     "Default is false to pair with deathIsPermanent=false (the default): if the " +
                     "pet is going to be resummoned, scattering its saddle/armor/chest contents " +
                     "across the death site is just an item-recovery chore. Set to true if you " +
                     "want vanilla drop behavior, e.g. when running deathIsPermanent=true. " +
                     "Note: ignored when deathIsPermanent=true — the bond is stripped before " +
                     "the drops event fires, so vanilla drops always happen in that mode.")
            .define("dropLootOnDeath", false);

    public static final ModConfigSpec.IntValue REVIVAL_COOLDOWN_SECONDS = BUILDER
            .comment("Per-bond cooldown (in seconds) after a non-permanent death before the bond can be summoned again. " +
                     "0 disables. Adds weight to deaths without going full permadeath. Has no effect when deathIsPermanent=true.")
            .defineInRange("revivalCooldownSeconds", 0, 0, 86400);

    // ───── Input (hold-to-confirm) ─────

    public static final ModConfigSpec.DoubleValue HOLD_TO_SUMMON_SECONDS = BUILDER
            .comment("Seconds to hold the summon keybind (or screen Summon button) to confirm summoning.")
            .defineInRange("holdToSummonSeconds", 1.0D, 0.1D, 10.0D);

    public static final ModConfigSpec.DoubleValue HOLD_TO_DISMISS_SECONDS = BUILDER
            .comment("Seconds to hold the summon keybind (or screen Dismiss button) to confirm dismissing the active pet.")
            .defineInRange("holdToDismissSeconds", 1.0D, 0.1D, 10.0D);

    public static final ModConfigSpec.BooleanValue CANCEL_HOLD_ON_DAMAGE = BUILDER
            .comment("If true, taking damage cancels any in-progress summon/dismiss hold (mirrors vanilla bow-draw / eating interrupt).")
            .define("cancelHoldOnDamage", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ───── ms helpers ─────
    // Internal code wants milliseconds for time math; configs are in seconds for users.

    public static long holdToDismissMs() {
        return Math.round(HOLD_TO_DISMISS_SECONDS.get() * 1000.0D);
    }

    public static long holdToSummonMs() {
        return Math.round(HOLD_TO_SUMMON_SECONDS.get() * 1000.0D);
    }

    public static long summonGlobalCooldownMs() {
        return SUMMON_GLOBAL_COOLDOWN_SECONDS.get() * 1000L;
    }

    public static long revivalCooldownMs() {
        return REVIVAL_COOLDOWN_SECONDS.get() * 1000L;
    }

    private Config() {}
}
