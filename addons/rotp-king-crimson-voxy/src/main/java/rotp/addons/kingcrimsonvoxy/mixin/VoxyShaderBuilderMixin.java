package rotp.addons.kingcrimsonvoxy.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.addons.kingcrimsonvoxy.KcVoxyHooks;

/** Every Voxy program is built here: one that holds a patch and does not compile is built again from Voxy's own text. */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.gl.shader.Shader$Builder", remap = false)
public abstract class VoxyShaderBuilderMixin {
    @Shadow
    @Final
    private Map<Object, String> sources;

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void rotpKcVoxy$guardCompile(CallbackInfoReturnable<Object> cir) {
        Object compiled = KcVoxyHooks.compileGuarded(this, this.sources);
        if (compiled != null) {
            cir.setReturnValue(compiled);
        }
    }
}
