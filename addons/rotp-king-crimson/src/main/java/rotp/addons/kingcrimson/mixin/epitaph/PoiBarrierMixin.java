package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** Points of interest are found in a room and never claimed, released, added or removed. */
@Mixin(value = PoiManager.class, priority = 900)
public abstract class PoiBarrierMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomAdd(BlockPos pos, Holder<PoiType> type, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomRemove(BlockPos pos, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }

    @Inject(method = "release", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomRelease(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(false);
        }
    }
}
