package rotp.core.mixin.timerate;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import rotp.core.api.time.EntityTimeRates;
import rotp.core.api.time.LevelTimeRates;
import rotp.core.subsystems.time.LevelTickRateState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;

/**
 * A server level's scheduled fluid and block ticks: the delay of each one scheduled is divided by the level's rate of
 * its channel, the fraction carried from one to the next so that their average is exact, at least one tick (a tick
 * scheduled now cannot run in this one). Ticks scheduled before a rate changed keep their time.
 */
@Mixin(LevelTicks.class)
public abstract class LevelTicksRateMixin implements LevelTickRateState.ScheduledTicks {
	@Unique @Nullable private ServerLevel jojo_ripples$rateLevel;
	@Unique @Nullable private LevelTimeRates.Channel jojo_ripples$rateChannel;
	@Unique private long jojo_ripples$rateTick = Long.MIN_VALUE;
	@Unique private float jojo_ripples$rate = 1.0F;
	@Unique private double jojo_ripples$delayCarry;

	@Override
	public void jojo_ripples$bindTickRate(ServerLevel level, LevelTimeRates.Channel channel) {
		jojo_ripples$rateLevel = level;
		jojo_ripples$rateChannel = channel;
	}

	@Override
	@Nullable
	public ServerLevel jojo_ripples$tickLevel() {
		return jojo_ripples$rateLevel;
	}

	@Override
	public boolean jojo_ripples$isFluidTicks() {
		return jojo_ripples$rateChannel == LevelTimeRates.Channel.FLUID_TICKS;
	}

	@ModifyVariable(method = "schedule", at = @At("HEAD"), argsOnly = true, require = 1)
	private ScheduledTick<?> jojo_ripples$scaleDelay(ScheduledTick<?> tick) {
		ServerLevel level = jojo_ripples$rateLevel;
		if (level == null || !LevelTimeRates.hasProviders()) {
			return tick;
		}
		long now = level.getGameTime();
		if (now != jojo_ripples$rateTick) {
			jojo_ripples$rateTick = now;
			jojo_ripples$rate = Math.min(LevelTimeRates.rate(jojo_ripples$rateChannel, level), EntityTimeRates.MAX_RATE);
		}
		long delay = tick.triggerTick() - now;
		if (jojo_ripples$rate == 1.0F || delay <= 0) {
			return tick;
		}
		double scaled = delay / (double) jojo_ripples$rate + jojo_ripples$delayCarry;
		long scaledDelay = (long) Math.floor(scaled);
		if (scaledDelay < 1) {
			scaledDelay = 1;
			jojo_ripples$delayCarry = 0.0D;
		}
		else {
			jojo_ripples$delayCarry = scaled - scaledDelay;
		}
		return scaledDelay == delay ? tick : LevelTickRateState.rescheduled(tick, now + scaledDelay);
	}
}
