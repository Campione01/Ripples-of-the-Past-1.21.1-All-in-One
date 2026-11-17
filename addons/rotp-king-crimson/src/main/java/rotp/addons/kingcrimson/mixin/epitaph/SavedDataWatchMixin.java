package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.saveddata.SavedData;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** Saved data marked dirty from a room cannot be undone: it is counted, so that it shows. */
@Mixin(value = SavedData.class, priority = 900)
public abstract class SavedDataWatchMixin {
    @Inject(method = "setDirty()V", at = @At("HEAD"))
    private void kingCrimson$roomDirty(CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.leak("room code marked saved data dirty: " + getClass().getName());
        }
    }
}
