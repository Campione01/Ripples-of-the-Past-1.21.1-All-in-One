package rotp.addons.gratefuldead.service;

import rotp.addons.gratefuldead.GratefulDeadConfig;
import rotp.addons.gratefuldead.compat.TemperatureCompat;
import rotp.addons.gratefuldead.item.IceCubeItem;
import rotp.addons.gratefuldead.state.AgingRateState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Next Album 0.1.13 aging rate (AgingSpeedUtil, AgingRateWindow): a signed
 * rate inside a window set by the temperature; the plains sit at +0.2, a
 * desert at the top, the cold below zero, where aging recedes instead.
 */
public final class AgingRate {
    public static final float HARD_MIN = -1.8F;
    public static final float HARD_MAX = 1.8F;
    public static final float PLAINS_TEMP = 0.8F;
    public static final float PLAINS_BASE = 0.2F;
    public static final float ENV_BASE_SLIDE = 0.55F;
    public static final float RANGE_SLIDE = 0.45F;
    public static final float BASE_HALF_SPAN = 1.15F;
    public static final float MIN_SIDE_SPAN = 0.35F;
    public static final float RATE_TO_SPEED = 5.0F;
    public static final float MIN_SPEED = 0.08F;
    public static final float MAX_SPEED = 2.6F;
    public static final float COLD_ENV_NORM = -0.85F;
    public static final float HELD_ICE_WINDOW_MIN = -0.35F;
    public static final float HELD_ICE_WINDOW_MAX = -0.22F;
    public static final float HELD_ICE_COOL = 0.28F;

    private AgingRate() {}

    public record Window(float min, float max) {
        public Window {
            float clampedMin = Mth.clamp(min, HARD_MIN, HARD_MAX);
            float clampedMax = Mth.clamp(max, HARD_MIN, HARD_MAX);
            if (clampedMax < clampedMin + MIN_SIDE_SPAN * 2.0F) {
                float mid = (clampedMin + clampedMax) * 0.5F;
                clampedMin = Mth.clamp(mid - MIN_SIDE_SPAN, HARD_MIN, HARD_MAX);
                clampedMax = Mth.clamp(mid + MIN_SIDE_SPAN, HARD_MIN, HARD_MAX);
            }
            min = clampedMin;
            max = clampedMax;
        }

        static Window fromEnvNorm(float envNorm) {
            float clamped = Mth.clamp(envNorm, -1.25F, 1.25F);
            return new Window(
                    Math.min(-BASE_HALF_SPAN + RANGE_SLIDE * clamped, -MIN_SIDE_SPAN),
                    Math.max(BASE_HALF_SPAN + RANGE_SLIDE * clamped, MIN_SIDE_SPAN));
        }

        Window shifted(float dMin, float dMax) {
            return new Window(min + dMin, max + dMax);
        }

        public float clamp(float rate) {
            return Mth.clamp(rate, min, max);
        }
    }

    public record Snapshot(float min, float max, float base, float actual) {}

    /** Minecraft biome temperature to the 0.1.13 scale: plains 0, desert +1, ice spikes -1. */
    public static float normalizeMcTemp(float temperature) {
        return temperature >= PLAINS_TEMP
                ? (temperature - PLAINS_TEMP) / 1.2F
                : (temperature - PLAINS_TEMP) / 1.5F;
    }

    public static Snapshot snapshot(Level level, BlockPos pos, LivingEntity entity) {
        float envNorm = TemperatureCompat.environmentNorm(level, pos, entity);
        Window window = Window.fromEnvNorm(envNorm);
        if (entity instanceof Player && IceCubeItem.isProvidingCooling(entity)) {
            window = window.shifted(HELD_ICE_WINDOW_MIN, HELD_ICE_WINDOW_MAX);
        }
        float extra = TemperatureCompat.bodyZeroOffset(entity)
                + TemperatureCompat.vanillaLocalOffset(level, pos, entity);
        float base = window.clamp(
                PLAINS_BASE + ENV_BASE_SLIDE * Mth.clamp(envNorm, -1.25F, 1.25F) + extra);
        float actual = window.clamp(base + modifiers(entity));
        return new Snapshot(window.min(), window.max(), base, actual);
    }

    static float modifiers(LivingEntity entity) {
        if (entity == null) {
            return 0.0F;
        }
        float modifiers = TemperatureCompat.itemOffset(entity);
        AgingRateState state = AgingRateState.getIfPresent(entity);
        if (state != null) {
            modifiers += state.exertion() + state.digestion() - state.currentIceCool()
                    - state.downedCool();
        }
        if (IceCubeItem.isProvidingCooling(entity)
                && (!(entity instanceof Player player)
                        || IceCubeItem.useCooling(player) <= 0.0F)) {
            modifiers -= HELD_ICE_COOL;
        }
        return modifiers;
    }

    /** The signed aging speed: 1 on the plains, below 0 where aging recedes. */
    public static float toSpeed(float actual) {
        float speed = RATE_TO_SPEED * actual;
        if (speed > 0.0F) {
            speed = Mth.clamp(speed, MIN_SPEED, MAX_SPEED);
        }
        else if (speed < 0.0F) {
            speed = Mth.clamp(speed, -MAX_SPEED, -MIN_SPEED);
        }
        return speed * GratefulDeadConfig.agingSpeedMultiplier();
    }

    public static float speed(Level level, BlockPos pos) {
        return level == null || pos == null
                ? 0.0F
                : toSpeed(snapshot(level, pos, null).actual());
    }

    public static float speed(LivingEntity entity) {
        return entity == null
                ? 0.0F
                : toSpeed(snapshot(entity.level(), entity.blockPosition(), entity).actual());
    }

    /** 0.1.13 isColdBiome: only the frozen biomes stop aging outright. */
    public static boolean isCold(Level level, BlockPos pos) {
        return TemperatureCompat.environmentNorm(level, pos, null) <= COLD_ENV_NORM;
    }
}
