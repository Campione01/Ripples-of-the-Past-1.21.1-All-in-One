package rotp.addons.spin.mixin;

import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rotp.addons.spin.compat.SpinLoveTrain;

@Pseudo
@Mixin(targets = "rotp.addons.d4c.D4CGameEvents", remap = false)
abstract class SpinLoveTrainEffectsMixin {
    @Inject(method = "onMobEffectApplicable(Lnet/neoforged/neoforge/event/entity/living/MobEffectEvent$Applicable;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/event/entity/living/MobEffectEvent$Applicable;"
                            + "setResult(Lnet/neoforged/neoforge/event/entity/living/MobEffectEvent$Applicable$Result;)V"),
            cancellable = true, require = 0, expect = 1)
    private static void rotp_spin$skipLoveTrainEffectDenial(MobEffectEvent.Applicable event, CallbackInfo ci) {
        if (SpinLoveTrain.allowEffectApplication(event.getEntity(), event.getEffectInstance())) {
            ci.cancel();
        }
    }
}
