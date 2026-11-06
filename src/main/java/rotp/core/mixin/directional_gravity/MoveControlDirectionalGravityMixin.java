package rotp.core.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravityTransforms;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * A mob's yaw and steps turn inside its gravity frame, so the place it wants to go is handed to the control in that frame.
 */
@Mixin(MoveControl.class)
public abstract class MoveControlDirectionalGravityMixin {
	@Shadow
	@Final
	protected Mob mob;
	@Shadow
	protected double wantedX;
	@Shadow
	protected double wantedY;
	@Shadow
	protected double wantedZ;

	@WrapMethod(method = "tick")
	private void jojo_ripples$directionalGravityWantedPosition(Operation<Void> original) {
		Direction direction = DirectionalGravityApi.getEffectiveDirection(mob);
		if (direction == Direction.DOWN) {
			original.call();
			return;
		}
		Vec3 wanted = new Vec3(wantedX, wantedY, wantedZ);
		Vec3 local = DirectionalGravityTransforms.toLocal(direction, wanted.subtract(mob.position()))
				.add(mob.position());
		wantedX = local.x;
		wantedY = local.y;
		wantedZ = local.z;
		try {
			original.call();
		}
		finally {
			wantedX = wanted.x;
			wantedY = wanted.y;
			wantedZ = wanted.z;
		}
	}
}
