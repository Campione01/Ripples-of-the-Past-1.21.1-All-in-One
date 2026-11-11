package rotp.core.mixin.aipause;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import rotp.core.api.entity.MobAiPause;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/**
 * EnderDragon.aiStep runs its phases, flight, wings, head and block breaking itself, all of it skipped for a dragon
 * with NoAI: a paused one takes that branch and hovers where it is.
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonAiPauseMixin {
	@ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;isNoAi()Z"), require = 1)
	private boolean jojo_ripples$holdWhilePaused(boolean noAi) {
		return noAi || MobAiPause.isPaused((EnderDragon) (Object) this);
	}
}
