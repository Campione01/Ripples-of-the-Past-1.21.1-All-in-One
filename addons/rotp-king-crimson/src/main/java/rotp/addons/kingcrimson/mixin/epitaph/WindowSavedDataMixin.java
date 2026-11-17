package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.saveddata.SavedData;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/** Saved data of the world that changes while a participant acts cannot be put back: he is not pulled back. */
@Mixin(SavedData.class)
public abstract class WindowSavedDataMixin {
    @Inject(method = "setDirty()V", at = @At("HEAD"))
    private void kingCrimson$windowDirty(CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.savedDataChanged((SavedData) (Object) this);
    }
}
