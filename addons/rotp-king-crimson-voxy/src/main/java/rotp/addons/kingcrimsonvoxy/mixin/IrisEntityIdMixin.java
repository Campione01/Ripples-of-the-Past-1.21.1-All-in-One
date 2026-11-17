package rotp.addons.kingcrimsonvoxy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import rotp.addons.kingcrimsonvoxy.KcVoxyHooks;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CapturedRenderingState", remap = false)
public abstract class IrisEntityIdMixin {
    @ModifyVariable(method = "setCurrentEntity(I)V", at = @At("HEAD"), argsOnly = true)
    private int rotpKcVoxy$farShellId(int id) {
        return KcVoxyHooks.shellEntityId(id);
    }
}
