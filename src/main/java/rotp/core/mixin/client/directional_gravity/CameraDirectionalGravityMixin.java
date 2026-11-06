package rotp.core.mixin.client.directional_gravity;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravityTransforms;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

@Mixin(Camera.class)
public abstract class CameraDirectionalGravityMixin {
	// A change of gravity turns the view over this time rather than at once.
	@Unique
	private static final float jojo_ripples$TURN_MILLIS = 200.0F;
	@Unique
	private Direction jojo_ripples$shownDirection = Direction.DOWN;
	@Unique
	private final Quaternionf jojo_ripples$turnedFrom = new Quaternionf();
	@Unique
	private final Quaternionf jojo_ripples$turn = new Quaternionf();
	@Unique
	private static final Quaternionf jojo_ripples$IDENTITY = new Quaternionf();
	@Unique
	private long jojo_ripples$turnStart;

	@Shadow
	@Final
	private Quaternionf rotation;
	@Shadow
	@Final
	private Vector3f forwards;
	@Shadow
	@Final
	private Vector3f up;
	@Shadow
	@Final
	private Vector3f left;
	@Shadow
	private Vec3 position;

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Inject(method = "setup",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/Camera;"
							+ "setRotation(FFF)V",
					shift = At.Shift.AFTER))
	private void jojo_ripples$directionalGravityCameraRotation(
			BlockGetter level, Entity entity, boolean detached,
			boolean thirdPersonReverse, float partialTick,
			CallbackInfo ci) {
		jojo_ripples$applyGravityRotation(entity);
	}

	@Inject(method = "setup",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/Camera;"
							+ "setRotation(FF)V",
					shift = At.Shift.AFTER))
	private void jojo_ripples$directionalGravitySleepingCameraRotation(
			BlockGetter level, Entity entity, boolean detached,
			boolean thirdPersonReverse, float partialTick,
			CallbackInfo ci) {
		jojo_ripples$applyGravityRotation(entity);
	}

	@Unique
	private void jojo_ripples$applyGravityRotation(Entity entity) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		long now = Util.getMillis();
		if (direction != jojo_ripples$shownDirection) {
			jojo_ripples$turnedFrom.set(jojo_ripples$turn);
			jojo_ripples$turnStart = now;
			jojo_ripples$shownDirection = direction;
		}
		long turned = now - jojo_ripples$turnStart;
		// The raw angles do not change, so the renderer would not find the sections this turn brings into view.
		if (turned <= jojo_ripples$TURN_MILLIS * 1.5F) {
			Minecraft.getInstance().levelRenderer.needsUpdate();
		}
		float progress = Mth.clamp(turned / jojo_ripples$TURN_MILLIS, 0.0F, 1.0F);
		if (direction == Direction.DOWN && progress >= 1.0F) {
			jojo_ripples$turn.identity();
			return;
		}
		jojo_ripples$turnedFrom.slerp(jojo_ripples$frameRotation(direction),
				progress, jojo_ripples$turn);
		rotation.premul(jojo_ripples$turn);
		jojo_ripples$turn.transform(forwards);
		jojo_ripples$turn.transform(up);
		jojo_ripples$turn.transform(left);
	}

	@Unique
	private static Quaternionf jojo_ripples$frameRotation(Direction direction) {
		Matrix3f gravityFrame = new Matrix3f();
		gravityFrame.setColumn(0,
				jojo_ripples$toWorld(direction, 1.0F, 0.0F, 0.0F));
		gravityFrame.setColumn(1,
				jojo_ripples$toWorld(direction, 0.0F, 1.0F, 0.0F));
		gravityFrame.setColumn(2,
				jojo_ripples$toWorld(direction, 0.0F, 0.0F, 1.0F));
		return gravityFrame.getNormalizedRotation(new Quaternionf());
	}

	@Inject(method = "setup",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/Camera;"
							+ "setPosition(DDD)V",
					shift = At.Shift.AFTER))
	private void jojo_ripples$directionalGravityCameraPosition(
			BlockGetter level, Entity entity, boolean detached,
			boolean thirdPersonReverse, float partialTick,
			CallbackInfo ci) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction == Direction.DOWN
				&& jojo_ripples$turn.equals(jojo_ripples$IDENTITY, 1.0E-5F)) {
			return;
		}

		Vec3 base = new Vec3(
				Mth.lerp((double) partialTick, entity.xo, entity.getX()),
				Mth.lerp((double) partialTick, entity.yo, entity.getY()),
				Mth.lerp((double) partialTick, entity.zo, entity.getZ()));
		double smoothedEyeHeight = position.distanceTo(base);
		// a change of gravity keeps the body's centre, so the eye swings about that
		double halfHeight = entity.getBbHeight() / 2.0;
		Vec3 center = DirectionalGravityTransforms.eyePosition(
				direction, base, halfHeight);
		Vector3f eye = jojo_ripples$turn.transform(
				new Vector3f(0.0F, (float) (smoothedEyeHeight - halfHeight), 0.0F));
		setPosition(center.add(eye.x, eye.y, eye.z));
	}

	@Unique
	private static Vector3f jojo_ripples$toWorld(Direction direction,
			float x, float y, float z) {
		Vec3 transformed = DirectionalGravityTransforms.toWorld(
				direction, new Vec3(x, y, z));
		return new Vector3f((float) transformed.x,
				(float) transformed.y, (float) transformed.z);
	}
}
