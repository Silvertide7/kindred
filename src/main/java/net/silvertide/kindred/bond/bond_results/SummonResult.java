package net.silvertide.kindred.bond.bond_results;

import java.util.Optional;

public enum SummonResult {
    WALKING(null),
    TELEPORTED_NEAR(null),
    SUMMONED_FRESH(null),
    NO_SUCH_BOND("kindred.summon.no_such_bond"),
    ON_COOLDOWN("kindred.summon.on_cooldown"),
    GLOBAL_COOLDOWN("kindred.summon.global_cooldown"),
    REVIVAL_PENDING("kindred.summon.reviving"),
    NO_SPACE("kindred.summon.no_space"),
    PLAYER_AIRBORNE("kindred.summon.player_airborne"),
    CROSS_DIM_BLOCKED("kindred.summon.cross_dim_blocked"),
    BANNED_DIMENSION("kindred.summon.banned_dimension"),
    BANNED_BIOME("kindred.summon.banned_biome"),
    SPAWN_FAILED(null);

    private final String translationKey;

    SummonResult(String translationKey) {
        this.translationKey = translationKey;
    }

    public Optional<String> translationKey() {
        return Optional.ofNullable(translationKey);
    }

    public boolean isSuccess() {
        return this == WALKING || this == TELEPORTED_NEAR || this == SUMMONED_FRESH;
    }
}
