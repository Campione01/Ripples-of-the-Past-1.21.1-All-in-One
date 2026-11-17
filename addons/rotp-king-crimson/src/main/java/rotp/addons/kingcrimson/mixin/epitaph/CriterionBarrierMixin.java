package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** No criterion is triggered in a room. */
@Mixin(value = SimpleCriterionTrigger.class, priority = 900)
public abstract class CriterionBarrierMixin {
    @Inject(method = "trigger", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomTrigger(ServerPlayer player, Predicate<?> testTrigger, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }
}
