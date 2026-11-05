package rotp.addons.highwaystar;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HighwayStarConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<Double> DRAIN_DAMAGE;
    public static final ModConfigSpec.ConfigValue<Double> FEED_HEALTH_AMOUNT;
    public static final ModConfigSpec.ConfigValue<Double> FEED_HUNGER_AMOUNT;
    public static final ModConfigSpec.ConfigValue<Integer> MAX_SOLES_COUNT;
    public static final ModConfigSpec.ConfigValue<Integer> TRACKING_MAX_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Highway Star Stand Settings").push("highwaystar");
        DRAIN_DAMAGE = builder.comment("Base damage dealt by each sole per second")
                .translation("highwaystar.configuration.highwaystar.drainDamage")
                .define("drainDamage", 2.0);
        FEED_HEALTH_AMOUNT = builder.comment("Health healed to the user per sole per second")
                .translation("highwaystar.configuration.highwaystar.feedHealthAmount")
                .define("feedHealthAmount", 0.2);
        FEED_HUNGER_AMOUNT = builder.comment("Hunger restored to the user per sole per second")
                .translation("highwaystar.configuration.highwaystar.feedHungerAmount")
                .define("feedHungerAmount", 0.5);
        MAX_SOLES_COUNT = builder.comment("Maximum number of soles spawned when splitting (no upper limit)")
                .translation("highwaystar.configuration.highwaystar.maxSolesCount")
                .define("maxSolesCount", 8);
        TRACKING_MAX_TICKS = builder.comment("Maximum tracking time for soles in ticks (20 ticks = 1 second)")
                .translation("highwaystar.configuration.highwaystar.trackingMaxTicks")
                .define("trackingMaxTicks", 600);
        builder.pop();
        SPEC = builder.build();
    }

    private HighwayStarConfig() {}
}
