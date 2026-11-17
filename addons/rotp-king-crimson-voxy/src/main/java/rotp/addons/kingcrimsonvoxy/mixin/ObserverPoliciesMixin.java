package rotp.addons.kingcrimsonvoxy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.addons.kingcrimsonvoxy.KcVoxyHooks;
import rotp.core.api.client.render.ObserverWorldRenderPolicies;
import rotp.core.api.client.render.ObserverWorldRenderPolicyProvider.Pass;

/** The terrain pass is what calls Voxy: while the far effect runs it stays on, and draws no real sections. */
@Mixin(value = ObserverWorldRenderPolicies.class, remap = false)
public abstract class ObserverPoliciesMixin {
    @Inject(method = "suppresses", at = @At("HEAD"), cancellable = true)
    private static void rotpKcVoxy$keepTerrainPass(Pass pass, CallbackInfoReturnable<Boolean> cir) {
        if (pass == Pass.TERRAIN && KcVoxyHooks.live()) {
            cir.setReturnValue(false);
        }
    }
}
