package rotp.core.mixin.client.controls;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.impl.stands.crazydiamond.client.MisshapenControls;

import net.minecraft.client.MouseHandler;

// Misshapen arms: the press arrives as the other button everywhere at once (vanilla keys, the Stand's click abilities)
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMisshapenArmsMixin {
	@Unique
	private boolean jojo_ripples$rerouting;

	@Shadow
	private void onPress(long windowPointer, int button, int action, int modifiers) {}

	@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$swapMisshapenArms(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
		if (jojo_ripples$rerouting) {
			return;
		}
		int routed = MisshapenControls.routeMouseButton(button, action);
		if (routed != button) {
			ci.cancel();
			jojo_ripples$rerouting = true;
			try {
				onPress(windowPointer, routed, action, modifiers);
			}
			finally {
				jojo_ripples$rerouting = false;
			}
		}
	}
}
