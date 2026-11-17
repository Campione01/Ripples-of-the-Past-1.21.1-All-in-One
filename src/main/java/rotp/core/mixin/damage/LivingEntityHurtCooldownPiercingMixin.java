package rotp.core.mixin.damage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import rotp.core.api.damage.DefencePiercing;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/** A hit that pierces invulnerability (DefencePiercing) passes the hurt cooldown as minecraft:bypasses_cooldown does. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtCooldownPiercingMixin {
	@WrapOperation(method = "hurt", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"), require = 1)
	private boolean jojo_ripples$piercedCooldown(DamageSource source, TagKey<DamageType> tag, Operation<Boolean> original) {
		return original.call(source, tag)
				|| tag == DamageTypeTags.BYPASSES_COOLDOWN && DefencePiercing.piercesInvulnerability(source);
	}
}
