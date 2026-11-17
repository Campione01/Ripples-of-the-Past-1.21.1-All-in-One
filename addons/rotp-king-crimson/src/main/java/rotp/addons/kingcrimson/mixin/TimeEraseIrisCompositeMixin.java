package rotp.addons.kingcrimson.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterVoidDepth;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class TimeEraseIrisCompositeMixin {
    @Inject(method = "finalizeLevelRendering()V", at = @At("HEAD"))
    private void beforeComposites(CallbackInfo callback) {
        KingCrimsonShatterVoidDepth.beforeComposites();
    }
}
