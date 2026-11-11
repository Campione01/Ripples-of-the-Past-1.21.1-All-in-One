package rotp.core.mixin.aipause;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.api.entity.MobAiPause;

import net.minecraft.world.entity.Mob;

/** MobAiPause: the pause takes the place of the mob's AI step and counts down at the end of each of its ticks. */
@Mixin(Mob.class)
public abstract class MobAiPauseMixin implements MobAiPause.Holder {
	@Unique private int jojo_ripples$aiPauseTicks;

	@Override
	public int jojo_ripples$aiPauseTicks() {
		return jojo_ripples$aiPauseTicks;
	}

	@Override
	public void jojo_ripples$setAiPauseTicks(int ticks) {
		jojo_ripples$aiPauseTicks = Math.max(ticks, 0);
	}

	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true, require = 1)
	private void jojo_ripples$holdPausedAi(CallbackInfo ci) {
		if (jojo_ripples$aiPauseTicks > 0) {
			MobAiPause.holdStill((Mob) (Object) this);
			ci.cancel();
		}
	}

	@Inject(method = "tick", at = @At("TAIL"), require = 1)
	private void jojo_ripples$countAiPause(CallbackInfo ci) {
		if (jojo_ripples$aiPauseTicks > 0) {
			jojo_ripples$aiPauseTicks--;
		}
	}
}
