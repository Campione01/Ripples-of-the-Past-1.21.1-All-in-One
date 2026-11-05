package rotp.addons.survivor;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SurvivorConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SURVIVOR_RANGE;
    public static final ModConfigSpec.DoubleValue TARGET_RANGE;
    public static final ModConfigSpec.DoubleValue MOB_HIT_RANGE;
    public static final ModConfigSpec.DoubleValue PLAYER_HIT_RANGE;
    public static final ModConfigSpec.DoubleValue HIT_DAMAGE;
    public static final ModConfigSpec.DoubleValue MOVE_SPEED;
    public static final ModConfigSpec.DoubleValue FORCED_SPEED_ATTRIBUTE;
    public static final ModConfigSpec.IntValue ATTACK_COOLDOWN;
    public static final ModConfigSpec.IntValue STUCK_TIME;
    public static final ModConfigSpec.IntValue WET_DURATION;
    public static final ModConfigSpec.IntValue WARMUP_DURATION;
    public static final ModConfigSpec.IntValue ANGER_DURATION;
    public static final ModConfigSpec.IntValue MAX_ANGER;
    public static final ModConfigSpec.IntValue ANGER_GROWTH_MIN;
    public static final ModConfigSpec.IntValue ANGER_GROWTH_MAX;
    public static final ModConfigSpec.IntValue ANGER_GROWTH_RAIN_MIN;
    public static final ModConfigSpec.IntValue ANGER_GROWTH_RAIN_MAX;
    public static final ModConfigSpec.BooleanValue PLAYER_ANGER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Survivor Stand Settings").push("survivor");
        SURVIVOR_RANGE = builder.comment("Survivor user influence range (blocks)")
                .translation("survivor.configuration.survivor.survivorRange")
                .defineInRange("survivorRange", 30.0, 1.0, 200.0);
        TARGET_RANGE = builder.comment("Mob target search range (blocks)")
                .translation("survivor.configuration.survivor.targetRange")
                .defineInRange("targetRange", 16.0, 1.0, 200.0);
        MOB_HIT_RANGE = builder.comment("Mob hit detection range (blocks)")
                .translation("survivor.configuration.survivor.mobHitRange")
                .defineInRange("mobHitRange", 1.5, 0.1, 50.0);
        PLAYER_HIT_RANGE = builder.comment("Player auto-attack range (blocks)")
                .translation("survivor.configuration.survivor.playerHitRange")
                .defineInRange("playerHitRange", 1.0, 0.1, 50.0);
        HIT_DAMAGE = builder.comment("Damage per hit")
                .translation("survivor.configuration.survivor.hitDamage")
                .defineInRange("hitDamage", 1.0, 0.0, 1000.0);
        MOVE_SPEED = builder.comment("Pathfinding speed multiplier (higher = faster)")
                .translation("survivor.configuration.survivor.moveSpeed")
                .defineInRange("moveSpeed", 0.6, 0.1, 10.0);
        FORCED_SPEED_ATTRIBUTE = builder.comment("Forced movement speed attribute (zombie default is 0.23)")
                .translation("survivor.configuration.survivor.forcedSpeedAttribute")
                .defineInRange("forcedSpeedAttribute", 0.6, 0.0, 10.0);
        ATTACK_COOLDOWN = builder.comment("Attack cooldown in ticks (20 = 1 second)")
                .translation("survivor.configuration.survivor.attackCooldown")
                .defineInRange("attackCooldown", 20, 0, 1000);
        STUCK_TIME = builder.comment("Ticks before forcing movement when stuck (40 = 2 seconds)")
                .translation("survivor.configuration.survivor.stuckTime")
                .defineInRange("stuckTime", 40, 0, 10000);
        WET_DURATION = builder.comment("Wet status duration in ticks (600 = 30 seconds)")
                .translation("survivor.configuration.survivor.wetDuration")
                .defineInRange("wetDuration", 600, 0, 100000);
        WARMUP_DURATION = builder.comment("Anger warmup duration in ticks (100 = 5 seconds)")
                .translation("survivor.configuration.survivor.warmupDuration")
                .defineInRange("warmupDuration", 100, 0, 100000);
        ANGER_DURATION = builder.comment("Anger duration in ticks (600 = 30 seconds)")
                .translation("survivor.configuration.survivor.angerDuration")
                .defineInRange("angerDuration", 600, 0, 100000);
        MAX_ANGER = builder.comment("Maximum anger value")
                .translation("survivor.configuration.survivor.maxAnger")
                .defineInRange("maxAnger", 100, 1, 100000);
        ANGER_GROWTH_MIN = builder.comment("Minimum anger growth per tick (normal)")
                .translation("survivor.configuration.survivor.angerGrowthMin")
                .defineInRange("angerGrowthMin", 0, 0, 1000);
        ANGER_GROWTH_MAX = builder.comment("Maximum anger growth per tick (normal)")
                .translation("survivor.configuration.survivor.angerGrowthMax")
                .defineInRange("angerGrowthMax", 5, 0, 1000);
        ANGER_GROWTH_RAIN_MIN = builder.comment("Minimum anger growth per tick (in rain)")
                .translation("survivor.configuration.survivor.angerGrowthRainMin")
                .defineInRange("angerGrowthRainMin", 5, 0, 1000);
        ANGER_GROWTH_RAIN_MAX = builder.comment("Maximum anger growth per tick (in rain)")
                .translation("survivor.configuration.survivor.angerGrowthRainMax")
                .defineInRange("angerGrowthRainMax", 20, 0, 1000);
        PLAYER_ANGER = builder.comment("Whether any player can get wet and angry")
                .translation("survivor.configuration.survivor.playerAnger")
                .define("playerAnger", false);
        builder.pop();
        SPEC = builder.build();
    }

    private SurvivorConfig() {}
}
