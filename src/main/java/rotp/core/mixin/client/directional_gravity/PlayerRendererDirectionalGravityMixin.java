package rotp.core.mixin.client.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravityTransforms;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererDirectionalGravityMixin {
	/** The offset that sinks a crouching body towards its floor is applied in the world, before the body is turned. */
	@Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)"
			+ "Lnet/minecraft/world/phys/Vec3;",
			at = @At("RETURN"), cancellable = true)
	private void jojo_ripples$directionalGravityCrouchOffset(
			AbstractClientPlayer player, float partialTicks,
			CallbackInfoReturnable<Vec3> cir) {
		Direction direction = DirectionalGravityApi.getEffectiveDirection(player);
		if (direction != Direction.DOWN) {
			cir.setReturnValue(DirectionalGravityTransforms.toWorld(direction, cir.getReturnValue()));
		}
	}
}
