package rotp.core.mixin.aipause;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import rotp.core.api.entity.MobAiPause;

import net.minecraft.world.entity.boss.wither.WitherBoss;

/** WitherBoss.aiStep flies at its target and turns its heads to theirs outside the AI step: a paused one has none. */
@Mixin(WitherBoss.class)
public abstract class WitherBossAiPauseMixin {
	@ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;getAlternativeTarget(I)I"), require = 1)
	private int jojo_ripples$noTargetWhilePaused(int targetId) {
		return MobAiPause.isPaused((WitherBoss) (Object) this) ? 0 : targetId;
	}
}
