package rotp.core.mixin.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.time.LevelTickRateState;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;

// a tick scheduled into a stopped chunk: its delay is counted from the stop's end (TimeStopScheduledTickHold)
@Mixin(LevelTicks.class)
public abstract class LevelTicksTimeStopMixin {
	@Inject(method = "schedule", at = @At("HEAD"), require = 1)
	private void jojo_ripples$noteTickScheduledInStoppedTime(ScheduledTick<?> tick, CallbackInfo ci) {
		LevelTickRateState.ScheduledTicks ticks = (LevelTickRateState.ScheduledTicks) this;
		ServerLevel level = ticks.jojo_ripples$tickLevel();
		if (level != null) {
			TimeStopState.noteScheduledTick(level, tick.pos(), tick.type(), ticks.jojo_ripples$isFluidTicks(), tick.triggerTick());
		}
	}
}
