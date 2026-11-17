package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Mob;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** A phantom does not despawn: the real players near its body are not in the room to keep it. */
@Mixin(value = Mob.class, priority = 900)
public abstract class MobBarrierMixin {
    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomCheckDespawn(CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }
}
