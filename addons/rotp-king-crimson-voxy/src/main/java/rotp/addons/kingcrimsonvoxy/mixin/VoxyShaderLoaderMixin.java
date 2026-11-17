package rotp.addons.kingcrimsonvoxy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.addons.kingcrimsonvoxy.KcVoxyFar;
import rotp.addons.kingcrimsonvoxy.KcVoxyShaderPatch;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.gl.shader.ShaderLoader", remap = false)
public abstract class VoxyShaderLoaderMixin {
    @Inject(method = "parse(Ljava/lang/String;)Ljava/lang/String;", at = @At("RETURN"), cancellable = true)
    private static void rotpKcVoxy$patch(String id, CallbackInfoReturnable<String> cir) {
        if (!KcVoxyShaderPatch.hasParser()) {
            KcVoxyFar.bindShaderParser();
        }
        cir.setReturnValue(KcVoxyShaderPatch.onParse(id, cir.getReturnValue()));
    }
}
