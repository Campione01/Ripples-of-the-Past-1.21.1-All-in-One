package rotp.addons.darkbluemoon;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

/** 1.16 DarkBlueMoonConfig: the same keys, comments, translation keys, defaults and ranges, registered as SERVER. */
public final class DarkBlueMoonConfig {
    public static final String FILE_NAME = "darkbluemoon-server.toml";

    public static final ModConfigSpec SPEC;
    public static final DoubleValue CHOP_WATER_DAMAGE;
    public static final DoubleValue CHOP_LAND_DAMAGE;
    public static final DoubleValue CHOP_WATER_RADIUS;
    public static final DoubleValue CHOP_LAND_RADIUS;
    public static final DoubleValue CHOP_WATER_KNOCKBACK;
    public static final DoubleValue CHOP_LAND_KNOCKBACK;
    public static final BooleanValue CHOP_BREAK_BLOCKS;
    public static final IntValue CHOP_BREAK_BLOCK_RADIUS;
    public static final DoubleValue CHOP_BLOCK_BREAK_DOT;
    public static final IntValue CHOP_BLOCK_BREAK_MAX_Y;
    public static final BooleanValue CHOP_BREAK_HARD_BLOCKS;
    public static final IntValue CHOP_DAMAGE_DELAY_TICKS;
    public static final IntValue BARNACLE_BIND_DURATION;
    public static final IntValue BARNACLE_TRAP_DURATION;
    public static final DoubleValue BARNACLE_BIND_RADIUS;
    public static final IntValue BARNACLE_MAX_COUNT;
    public static final IntValue SCALE_SHOT_COUNT;
    public static final DoubleValue SCALE_SHOT_WATER_DAMAGE;
    public static final DoubleValue SCALE_SHOT_LAND_DAMAGE;
    public static final IntValue SCALE_SHOT_WATER_LIFESPAN;
    public static final IntValue SCALE_SHOT_LAND_LIFESPAN;
    public static final IntValue SCALE_SHOT_MAX_AMMO;
    public static final IntValue SCALE_SHOT_REGEN_INTERVAL;
    public static final IntValue SCALE_SHOT_REGEN_AMOUNT;
    public static final IntValue WHIRLPOOL_CHARGE_TIME;
    public static final IntValue WHIRLPOOL_LIFESPAN;
    public static final DoubleValue WHIRLPOOL_RADIUS;
    public static final DoubleValue WHIRLPOOL_PULL_SPEED;
    public static final DoubleValue WHIRLPOOL_MIN_DAMAGE;
    public static final DoubleValue WHIRLPOOL_MAX_DAMAGE;
    public static final IntValue WHIRLPOOL_WATER_CHECK_RADIUS;
    public static final IntValue WHIRLPOOL_REQUIRED_WATER;
    public static final IntValue BARNACLE_MIN_COST;
    public static final IntValue BARNACLE_MAX_COST;
    public static final IntValue BARNACLE_NECKLACE_COST_DIVISOR;

    private static final String KEY = "darkbluemoon.configuration.darkbluemoon.";

    static {
        Builder builder = new Builder();
        builder.comment("Dark Blue Moon Stand Settings").push("darkbluemoon");
        CHOP_WATER_DAMAGE = builder.comment("Chop - Underwater Damage")
                .translation(KEY + "chopWaterDamage")
                .defineInRange("chopWaterDamage", 30.0, 0.0, 1000.0);
        CHOP_LAND_DAMAGE = builder.comment("Chop - Land Damage")
                .translation(KEY + "chopLandDamage")
                .defineInRange("chopLandDamage", 10.0, 0.0, 1000.0);
        CHOP_WATER_RADIUS = builder.comment("Chop - Underwater Attack Radius")
                .translation(KEY + "chopWaterRadius")
                .defineInRange("chopWaterRadius", 3.0, 0.0, 32.0);
        CHOP_LAND_RADIUS = builder.comment("Chop - Land Attack Radius")
                .translation(KEY + "chopLandRadius")
                .defineInRange("chopLandRadius", 1.5, 0.0, 32.0);
        CHOP_WATER_KNOCKBACK = builder.comment("Chop - Underwater Knockback")
                .translation(KEY + "chopWaterKnockback")
                .defineInRange("chopWaterKnockback", 2.5, 0.0, 32.0);
        CHOP_LAND_KNOCKBACK = builder.comment("Chop - Land Knockback")
                .translation(KEY + "chopLandKnockback")
                .defineInRange("chopLandKnockback", 0.5, 0.0, 32.0);
        CHOP_BREAK_BLOCKS = builder.comment("Chop - Break Blocks")
                .translation(KEY + "chopBreakBlocks")
                .define("chopBreakBlocks", true);
        CHOP_BREAK_BLOCK_RADIUS = builder.comment("Chop - Block Break Radius")
                .translation(KEY + "chopBreakBlockRadius")
                .defineInRange("chopBreakBlockRadius", 2, 0, 32);
        CHOP_BLOCK_BREAK_DOT = builder.comment("Chop - Block Break Front Angle")
                .translation(KEY + "chopBlockBreakDot")
                .defineInRange("chopBlockBreakDot", 0.3, -1.0, 1.0);
        CHOP_BLOCK_BREAK_MAX_Y = builder.comment("Chop - Block Break Max Height")
                .translation(KEY + "chopBlockBreakMaxY")
                .defineInRange("chopBlockBreakMaxY", 1, -16, 16);
        CHOP_BREAK_HARD_BLOCKS = builder.comment("Chop - Break Hard Blocks")
                .translation(KEY + "chopBreakHardBlocks")
                .define("chopBreakHardBlocks", false);
        CHOP_DAMAGE_DELAY_TICKS = builder.comment("Chop - Damage Delay Ticks")
                .translation(KEY + "chopDamageDelayTicks")
                .defineInRange("chopDamageDelayTicks", 10, 0, 200);
        BARNACLE_BIND_DURATION = builder.comment("Barnacle - Bind Duration")
                .translation(KEY + "barnacleBindDuration")
                .defineInRange("barnacleBindDuration", 200, 0, 20000);
        BARNACLE_TRAP_DURATION = builder.comment("Barnacle - Trap Duration")
                .translation(KEY + "barnacleTrapDuration")
                .defineInRange("barnacleTrapDuration", 1200, 0, 20000);
        BARNACLE_BIND_RADIUS = builder.comment("Barnacle - Bind Check Radius")
                .translation(KEY + "barnacleBindRadius")
                .defineInRange("barnacleBindRadius", 3.0, 0.0, 32.0);
        BARNACLE_MAX_COUNT = builder.comment("Barnacle - Max Entities")
                .translation(KEY + "barnacleMaxCount")
                .defineInRange("barnacleMaxCount", 50, 0, 500);
        SCALE_SHOT_COUNT = builder.comment("Scale Shot - Shots Per Use")
                .translation(KEY + "scaleShotCount")
                .defineInRange("scaleShotCount", 5, 1, 100);
        SCALE_SHOT_WATER_DAMAGE = builder.comment("Scale Shot - Underwater Damage")
                .translation(KEY + "scaleShotWaterDamage")
                .defineInRange("scaleShotWaterDamage", 5.0, 0.0, 1000.0);
        SCALE_SHOT_LAND_DAMAGE = builder.comment("Scale Shot - Land Damage")
                .translation(KEY + "scaleShotLandDamage")
                .defineInRange("scaleShotLandDamage", 2.0, 0.0, 1000.0);
        SCALE_SHOT_WATER_LIFESPAN = builder.comment("Scale Shot - Underwater Lifespan")
                .translation(KEY + "scaleShotWaterLifespan")
                .defineInRange("scaleShotWaterLifespan", 100, 0, 20000);
        SCALE_SHOT_LAND_LIFESPAN = builder.comment("Scale Shot - Land Lifespan")
                .translation(KEY + "scaleShotLandLifespan")
                .defineInRange("scaleShotLandLifespan", 20, 0, 20000);
        SCALE_SHOT_MAX_AMMO = builder.comment("Scale Shot - Max Ammo")
                .translation(KEY + "scaleShotMaxAmmo")
                .defineInRange("scaleShotMaxAmmo", 20, 1, 10000);
        SCALE_SHOT_REGEN_INTERVAL = builder.comment("Scale Shot - Regen Interval")
                .translation(KEY + "scaleShotRegenInterval")
                .defineInRange("scaleShotRegenInterval", 100, 1, 20000);
        SCALE_SHOT_REGEN_AMOUNT = builder.comment("Scale Shot - Regen Amount")
                .translation(KEY + "scaleShotRegenAmount")
                .defineInRange("scaleShotRegenAmount", 5, 1, 1000);
        WHIRLPOOL_CHARGE_TIME = builder.comment("Whirlpool - Charge Time")
                .translation(KEY + "whirlpoolChargeTime")
                .defineInRange("whirlpoolChargeTime", 200, 0, 20000);
        WHIRLPOOL_LIFESPAN = builder.comment("Whirlpool - Lifespan")
                .translation(KEY + "whirlpoolLifespan")
                .defineInRange("whirlpoolLifespan", 1000, 1, 100000);
        WHIRLPOOL_RADIUS = builder.comment("Whirlpool - Radius")
                .translation(KEY + "whirlpoolRadius")
                .defineInRange("whirlpoolRadius", 10.0, 0.0, 128.0);
        WHIRLPOOL_PULL_SPEED = builder.comment("Whirlpool - Pull Speed")
                .translation(KEY + "whirlpoolPullSpeed")
                .defineInRange("whirlpoolPullSpeed", 0.4, 0.0, 16.0);
        WHIRLPOOL_MIN_DAMAGE = builder.comment("Whirlpool - Min Damage")
                .translation(KEY + "whirlpoolMinDamage")
                .defineInRange("whirlpoolMinDamage", 1.0, 0.0, 1000.0);
        WHIRLPOOL_MAX_DAMAGE = builder.comment("Whirlpool - Max Damage")
                .translation(KEY + "whirlpoolMaxDamage")
                .defineInRange("whirlpoolMaxDamage", 5.0, 0.0, 1000.0);
        WHIRLPOOL_WATER_CHECK_RADIUS = builder.comment("Whirlpool - Water Check Radius")
                .translation(KEY + "whirlpoolWaterCheckRadius")
                .defineInRange("whirlpoolWaterCheckRadius", 10, 1, 128);
        WHIRLPOOL_REQUIRED_WATER = builder.comment("Whirlpool - Required Water")
                .translation(KEY + "whirlpoolRequiredWater")
                .defineInRange("whirlpoolRequiredWater", 200, 0, 100000);
        BARNACLE_MIN_COST = builder.comment("Barnacle Item - Min Cost")
                .translation(KEY + "barnacleMinCost")
                .defineInRange("barnacleMinCost", 15, 0, 10000);
        BARNACLE_MAX_COST = builder.comment("Barnacle Item - Max Cost")
                .translation(KEY + "barnacleMaxCost")
                .defineInRange("barnacleMaxCost", 20, 0, 10000);
        BARNACLE_NECKLACE_COST_DIVISOR = builder.comment("Barnacle Item - Necklace Cost Divisor")
                .translation(KEY + "barnacleNecklaceCostDivisor")
                .defineInRange("barnacleNecklaceCostDivisor", 3, 1, 100);
        builder.pop();
        SPEC = builder.build();
    }

    private DarkBlueMoonConfig() {}

    /**
     * A SERVER config is only loaded while a world runs (a client gets it on login), but a client's HUD asks the
     * conditions before that too; there the default is read.
     */
    public static <T> T get(ConfigValue<T> value) {
        try {
            return value.get();
        }
        catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }

    public static int getInt(IntValue value) {
        return get(value);
    }

    public static double getDouble(DoubleValue value) {
        return get(value);
    }

    public static boolean getBool(BooleanValue value) {
        return get(value);
    }
}
