package rotp.core.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravityTransforms;
import rotp.core.subsystems.directional_gravity.DirectionalGravityRuntime;
import rotp.core.subsystems.directional_gravity.DirectionalGravityRuntime.LocalFrame;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDirectionalGravityMixin {
	@Shadow
	protected abstract void updateWalkAnimation(float partialTick);

	/** Vanilla looks from above the feet of two upright bodies, which is inside the ceiling they hang from. */
	@Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$directionalGravityLineOfSight(
			Entity target, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity living = (LivingEntity) (Object) this;
		if (DirectionalGravityApi.getEffectiveDirection(living) == Direction.DOWN
				&& DirectionalGravityApi.getEffectiveDirection(target) == Direction.DOWN) {
			return;
		}
		Vec3 from = living.getEyePosition();
		Vec3 to = target.getEyePosition();
		cir.setReturnValue(target.level() == living.level()
				&& to.distanceTo(from) <= 128.0
				&& living.level().clip(new ClipContext(from, to,
						ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, living))
						.getType() == HitResult.Type.MISS);
	}

	/** A hit lifts its target off its own floor and pushes it along that floor. */
	@WrapMethod(method = "knockback")
	private void jojo_ripples$directionalGravityKnockback(
			double strength, double x, double z, Operation<Void> original) {
		LivingEntity living = (LivingEntity) (Object) this;
		Direction direction = DirectionalGravityApi.getEffectiveDirection(living);
		if (direction == Direction.DOWN
				|| DirectionalGravityRuntime.isLocalFrame(living)) {
			original.call(strength, x, z);
			return;
		}
		Vec3 local = DirectionalGravityTransforms.toLocal(direction, new Vec3(x, 0.0, z));
		try (LocalFrame ignored =
				DirectionalGravityRuntime.enterLocalFrame(living)) {
			original.call(strength, local.x, local.z);
		}
	}

	/** The body turns to where it walks on its own floor. */
	@ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
	private double jojo_ripples$directionalGravityBodyTurnX(double vanillaX) {
		Direction direction = DirectionalGravityApi.getEffectiveDirection((LivingEntity) (Object) this);
		return direction == Direction.DOWN ? vanillaX : jojo_ripples$walkedInFrame(direction).x;
	}

	@ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 1)
	private double jojo_ripples$directionalGravityBodyTurnZ(double vanillaZ) {
		Direction direction = DirectionalGravityApi.getEffectiveDirection((LivingEntity) (Object) this);
		return direction == Direction.DOWN ? vanillaZ : jojo_ripples$walkedInFrame(direction).z;
	}

	@Unique
	private Vec3 jojo_ripples$walkedInFrame(Direction direction) {
		LivingEntity living = (LivingEntity) (Object) this;
		return DirectionalGravityTransforms.toLocal(direction, new Vec3(
				living.getX() - living.xo, living.getY() - living.yo, living.getZ() - living.zo));
	}

	/** The legs walk with the distance walked on its own floor. */
	@Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$directionalGravityWalkAnimation(
			boolean includeHeight, CallbackInfo ci) {
		LivingEntity living = (LivingEntity) (Object) this;
		Direction direction = DirectionalGravityApi.getEffectiveDirection(living);
		if (direction != Direction.DOWN) {
			Vec3 local = jojo_ripples$walkedInFrame(direction);
			updateWalkAnimation((float) Mth.length(local.x, includeHeight ? local.y : 0.0, local.z));
			ci.cancel();
		}
	}

	@WrapMethod(method = "travel")
	private void jojo_ripples$directionalGravityTravelFrame(
			Vec3 travelVector, Operation<Void> original) {
		LivingEntity living = (LivingEntity) (Object) this;
		try (LocalFrame ignored =
				DirectionalGravityRuntime.enterLocalFrame(living)) {
			original.call(travelVector);
		}
	}

	@WrapOperation(method = "travel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/"
							+ "LivingEntity;isFree(DDD)Z"))
	private boolean jojo_ripples$directionalFluidExitProbe(
			LivingEntity living, double x, double y, double z,
			Operation<Boolean> original) {
		Direction direction =
				DirectionalGravityRuntime.localFrameDirection(living);
		if (direction == Direction.DOWN) {
			return original.call(living, x, y, z);
		}
		Vec3 localMovement = living.getDeltaMovement();
		Vec3 lastMove =
				DirectionalGravityRuntime.lastLocalMovement(living);
		Vec3 localProbe = new Vec3(
				localMovement.x,
				localMovement.y + 0.6 - lastMove.y,
				localMovement.z);
		Vec3 worldProbe = DirectionalGravityTransforms.toWorld(
				direction, localProbe);
		return original.call(living,
				worldProbe.x, worldProbe.y, worldProbe.z);
	}

	@WrapMethod(method = "jumpFromGround")
	private void jojo_ripples$directionalGravityJump(
			Operation<Void> original) {
		LivingEntity living = (LivingEntity) (Object) this;
		try (LocalFrame ignored =
				DirectionalGravityRuntime.enterLocalFrame(living)) {
			original.call();
		}
	}

	@WrapOperation(method = "aiStep",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/"
							+ "LivingEntity;jumpInFluid"
							+ "(Lnet/neoforged/neoforge/fluids/"
							+ "FluidType;)V"))
	private void jojo_ripples$directionalGravityFluidJump(
			LivingEntity living, FluidType fluid,
			Operation<Void> original) {
		try (LocalFrame ignored =
				DirectionalGravityRuntime.enterLocalFrame(living)) {
			original.call(living, fluid);
		}
	}

	@Inject(method = "getLocalBoundsForPose", at = @At("RETURN"),
			cancellable = true)
	private void jojo_ripples$directionalGravityPoseBounds(
			Pose pose, CallbackInfoReturnable<AABB> cir) {
		LivingEntity living = (LivingEntity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(living);
		if (direction != Direction.DOWN) {
			cir.setReturnValue(DirectionalGravityTransforms.rotateBox(
					direction, cir.getReturnValue(), Vec3.ZERO));
		}
	}

	@Inject(method = "wouldNotSuffocateAtTargetPose",
			at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$directionalGravityPoseCollision(
			Pose pose, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity living = (LivingEntity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(living);
		if (direction == Direction.DOWN) {
			return;
		}
		AABB vanillaBox =
				living.getDimensions(pose).makeBoundingBox(
						living.position());
		AABB rotatedBox = DirectionalGravityTransforms.rotateBox(
				direction, vanillaBox, living.position());
		cir.setReturnValue(
				living.level().noBlockCollision(living, rotatedBox));
	}
}
