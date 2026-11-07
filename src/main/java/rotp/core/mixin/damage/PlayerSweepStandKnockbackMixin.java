package rotp.core.mixin.damage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import rotp.core.powersystem.standpower.entity.StandEntity;
import com.llamalad7.mixinextras.injector.WrapWithCondition;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** The sweep's knockback comes before its hit: a Stand the hit cannot touch, and through it its user, must not take it. */
@Mixin(Player.class)
public abstract class PlayerSweepStandKnockbackMixin {

	@WrapWithCondition(method = "attack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V", ordinal = 1))
	private boolean jojo_ripples$noSweepKnockbackOnStands(LivingEntity swept, double strength, double xRatio, double zRatio) {
		return !(swept instanceof StandEntity);
	}
}
