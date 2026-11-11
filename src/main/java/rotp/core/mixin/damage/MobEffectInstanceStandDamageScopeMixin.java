package rotp.core.mixin.damage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import rotp.core.powersystem.standpower.StandDamageContext;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

// An effect a Stand gave ticks as the Stand's: the damage it does with no attacker is the Stand's work.
@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceStandDamageScopeMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/effect/MobEffect;applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z"))
	private boolean jojo_ripples$applyAsItsStands(MobEffect effect, LivingEntity entity, int amplifier, Operation<Boolean> original) {
		try (var scope = StandDamageContext.effectTicks((MobEffectInstance) (Object) this)) {
			return original.call(effect, entity, amplifier);
		}
	}
}
