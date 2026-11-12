package rotp.core.mixin.client.controls;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.InputConstants;

import rotp.core.impl.stands.crazydiamond.client.MisshapenControls;

import net.minecraft.client.KeyMapping;

// Misshapen arms: keyboard and mouse both reach the key mappings here, so a key bound to attack drives use and the other
// way round, held actions (mining, eating, drawing a bow) included
@Mixin(KeyMapping.class)
public abstract class KeyMappingMisshapenArmsMixin {
	@Unique
	private static boolean jojo_ripples$rerouting;

	@Inject(method = "set", at = @At("HEAD"), cancellable = true)
	private static void jojo_ripples$swapMisshapenArmsSet(InputConstants.Key key, boolean held, CallbackInfo ci) {
		if (jojo_ripples$rerouting) {
			return;
		}
		InputConstants.Key routed = MisshapenControls.routeSet(key, held);
		if (routed != key) {
			ci.cancel();
			jojo_ripples$rerouting = true;
			try {
				KeyMapping.set(routed, held);
			}
			finally {
				jojo_ripples$rerouting = false;
			}
		}
	}

	@Inject(method = "click", at = @At("HEAD"), cancellable = true)
	private static void jojo_ripples$swapMisshapenArmsClick(InputConstants.Key key, CallbackInfo ci) {
		if (jojo_ripples$rerouting) {
			return;
		}
		InputConstants.Key routed = MisshapenControls.routeClick(key);
		if (routed != key) {
			ci.cancel();
			jojo_ripples$rerouting = true;
			try {
				KeyMapping.click(routed);
			}
			finally {
				jojo_ripples$rerouting = false;
			}
		}
	}

	@Inject(method = "releaseAll", at = @At("HEAD"))
	private static void jojo_ripples$forgetMisshapenArmsKeys(CallbackInfo ci) {
		MisshapenControls.forgetHeldKeys();
	}
}
