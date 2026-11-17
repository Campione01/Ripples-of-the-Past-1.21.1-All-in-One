package rotp.addons.spin.mixin;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rotp.addons.spin.compat.SpinLoveTrain;

@Pseudo
@Mixin(targets = "rotp.addons.d4c.D4CCorpseService", remap = false)
abstract class SpinLoveTrainDamageMixin {
    @Inject(method = "modifyIncomingDamage(Lnet/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent;setCanceled(Z)V"),
            cancellable = true, require = 0, expect = 1)
    private static void rotp_spin$skipLoveTrainDamageBranch(LivingIncomingDamageEvent event, CallbackInfo ci) {
        // Leave the event untouched; only D4C's cancellation and synchronous redirection are skipped.
        if (SpinLoveTrain.consumeDamagePermit(event.getEntity(), event.getSource())) {
            ci.cancel();
        }
    }
}
