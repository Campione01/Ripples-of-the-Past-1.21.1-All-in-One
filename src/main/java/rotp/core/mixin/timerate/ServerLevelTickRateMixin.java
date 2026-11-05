package rotp.core.mixin.timerate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.api.time.LevelTimeRates;
import rotp.core.subsystems.time.LevelTickRateState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * The server level's side of the tick rates: random ticks, the delays of scheduled fluid and block ticks, and the extra
 * ticks of a non-living entity (its held-back ticks are refused in ServerLevelTimeStopTickMixin, after stopped time).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelTickRateMixin implements LevelTickRateState.Holder {
	@Unique private LevelTickRateState jojo_ripples$tickRateState;

	@Override
	public LevelTickRateState jojo_ripples$tickRateState() {
		if (jojo_ripples$tickRateState == null) {
			jojo_ripples$tickRateState = new LevelTickRateState();
		}
		return jojo_ripples$tickRateState;
	}

	@Inject(method = "<init>", at = @At("RETURN"), require = 1)
	private void jojo_ripples$bindScheduledTickRates(CallbackInfo ci) {
		ServerLevel level = (ServerLevel) (Object) this;
		((LevelTickRateState.ScheduledTicks) level.getFluidTicks()).jojo_ripples$bindTickRate(level, LevelTimeRates.Channel.FLUID_TICKS);
		((LevelTickRateState.ScheduledTicks) level.getBlockTicks()).jojo_ripples$bindTickRate(level, LevelTimeRates.Channel.BLOCK_TICKS);
	}

	@ModifyVariable(method = "tickChunk", at = @At("HEAD"), argsOnly = true, require = 1)
	private int jojo_ripples$scaleRandomTickSpeed(int randomTickSpeed) {
		return LevelTickRateState.randomTickSpeed((ServerLevel) (Object) this, randomTickSpeed);
	}

	@Inject(method = "tickNonPassenger", at = @At("TAIL"), require = 1)
	private void jojo_ripples$runExtraEntityTicks(Entity entity, CallbackInfo ci) {
		LevelTickRateState.endEntityTick((ServerLevel) (Object) this, entity);
	}
}
