package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** A ticket of a point of interest is neither taken nor given back in a room: taking answers as if it had worked. */
@Mixin(value = PoiRecord.class, priority = 900)
public abstract class PoiRecordBarrierMixin {
    @Inject(method = "acquireTicket", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomAcquire(CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "releaseTicket", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomRelease(CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(true);
        }
    }
}
