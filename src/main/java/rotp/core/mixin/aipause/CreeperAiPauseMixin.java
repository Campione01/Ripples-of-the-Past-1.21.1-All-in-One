package rotp.core.mixin.aipause;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.api.entity.MobAiPause;

import net.minecraft.world.entity.monster.Creeper;

/** The swell step of Creeper.tick runs before its AI step: a paused creeper's fuse goes down from the first tick. */
@Mixin(Creeper.class)
public abstract class CreeperAiPauseMixin {
	@Inject(method = "tick", at = @At("HEAD"), require = 1)
	private void jojo_ripples$holdPausedFuse(CallbackInfo ci) {
		Creeper creeper = (Creeper) (Object) this;
		if (MobAiPause.isPaused(creeper)) {
			MobAiPause.holdFuse(creeper);
		}
	}
}
