package rotp.addons.madeinheaven.state;

import rotp.core.api.time.EntityTimeRates;
import rotp.core.api.time.LevelTimeRates;
import rotp.core.customobjects.AfterimageEntity;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.level.Level;

/**
 * Made in Heaven's answers to the core's time-rate registries (A2-COMBINE:
 * the slowest rate any source gives wins). Both modes give the level's TIME
 * rate m; mode A answers for the beings it holds back (TimeLagService), mode
 * B runs the Overworld's non-living things and ticks m times faster and lets
 * the owner adapt.
 */
public final class TimeAccelerationRates {
    public static final ResourceLocation OWNER =
            MadeInHeavenAddon.id("time_acceleration");

    private TimeAccelerationRates() {}

    public static void register() {
        EntityTimeRates.register(OWNER, TimeAccelerationRates::entityRate);
        LevelTimeRates.register(OWNER, TimeAccelerationRates::levelRate);
    }

    static float entityRate(Entity entity, EntityTimeRates.Channel channel) {
        return switch (channel) {
            case ENTITY_TICK -> nonLivingTickRate(entity);
            case BEING -> entity instanceof LivingEntity living
                    ? TimeLagService.beingRate(living)
                    : EntityTimeRates.NO_RATE;
            case STAND_ACTIONS -> entity instanceof LivingEntity living
                    ? TimeLagService.standActionRate(living)
                    : EntityTimeRates.NO_RATE;
            case MOTION -> entity instanceof LivingEntity living
                    ? TimeLagService.motionRate(living)
                    : EntityTimeRates.NO_RATE;
            default -> EntityTimeRates.NO_RATE;
        };
    }

    // Asked for every non-living entity of every level each server tick.
    static float nonLivingTickRate(Entity entity) {
        Level level = entity.level();
        if (level.isClientSide()
                || level.dimension() != Level.OVERWORLD
                || entity instanceof LivingEntity) {
            return EntityTimeRates.NO_RATE;
        }
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (!state.worldAccelerates() || keepsOwnTime(entity)) {
            return EntityTimeRates.NO_RATE;
        }
        return TimeLagRates.lagMultiplier(state.phase());
    }

    // An afterimage follows the being it echoes, in that being's time; the
    // others change nothing over time and would only spend the core's
    // budget of extra entity ticks.
    private static boolean keepsOwnTime(Entity entity) {
        return entity instanceof AfterimageEntity
                || entity instanceof BlockAttachedEntity
                || entity instanceof Display
                || entity instanceof Marker
                || entity instanceof Interaction;
    }

    static float levelRate(Level level, LevelTimeRates.Channel channel) {
        if (level.dimension() != Level.OVERWORLD) {
            return LevelTimeRates.NO_RATE;
        }
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (!state.active()) {
            return LevelTimeRates.NO_RATE;
        }
        float multiplier = TimeLagRates.lagMultiplier(state.phase());
        return switch (channel) {
            case TIME -> multiplier;
            case RANDOM_TICKS, FLUID_TICKS, BLOCK_TICKS, BLOCK_ENTITIES ->
                    state.worldAccelerates()
                            ? multiplier
                            : LevelTimeRates.NO_RATE;
            default -> LevelTimeRates.NO_RATE;
        };
    }
}
