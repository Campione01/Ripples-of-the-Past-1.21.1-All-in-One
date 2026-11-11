package rotp.core.mixin.damage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import rotp.core.powersystem.standpower.StandDamageContext;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

// A Stand's body and the entities it made tick as the Stand's: what they hurt with no other attacker is its work.
@Mixin(ServerLevel.class)
public abstract class ServerLevelStandDamageScopeMixin {
	@WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
	private void jojo_ripples$tickAsItsStands(Entity entity, Operation<Void> original) {
		try (var scope = StandDamageContext.entityTicks(entity)) {
			original.call(entity);
		}
	}

	@WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
	private void jojo_ripples$rideTickAsItsStands(Entity passenger, Operation<Void> original) {
		try (var scope = StandDamageContext.entityTicks(passenger)) {
			original.call(passenger);
		}
	}
}
