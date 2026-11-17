package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** No advancement is earned in a room. */
@Mixin(value = PlayerAdvancements.class, priority = 900)
public abstract class AdvancementBarrierMixin {
    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomAward(AdvancementHolder advancement, String criterionKey, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(false);
        }
    }
}
