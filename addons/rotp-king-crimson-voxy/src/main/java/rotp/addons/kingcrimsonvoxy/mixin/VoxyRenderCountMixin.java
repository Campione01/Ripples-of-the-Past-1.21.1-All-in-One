package rotp.addons.kingcrimsonvoxy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.addons.kingcrimsonvoxy.KcVoxyHooks;

/** Probe and trace runs only (the plugin leaves it out otherwise): counts and times Voxy's real work per frame. */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.VoxyRenderSystem", remap = false)
public abstract class VoxyRenderCountMixin {
    @Inject(method = "renderOpaque(Lme/cortex/voxy/client/core/rendering/Viewport;)V", at = @At("HEAD"))
    private void rotpKcVoxy$count(@Coerce Object viewport, CallbackInfo ci) {
        if (viewport != null) {
            KcVoxyHooks.onRenderOpaque();
        }
    }

    @Inject(method = "renderOpaque(Lme/cortex/voxy/client/core/rendering/Viewport;)V", at = @At("RETURN"))
    private void rotpKcVoxy$done(@Coerce Object viewport, CallbackInfo ci) {
        if (viewport != null) {
            KcVoxyHooks.onRenderOpaqueEnd();
        }
    }
}
