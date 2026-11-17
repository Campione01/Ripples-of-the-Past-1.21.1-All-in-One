package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/** The journal of a window on what leaves the level: what a participant took away is kept, to be made again. */
@Mixin(Entity.class)
public abstract class WindowEntityMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void kingCrimson$windowEntityRemoving(Entity.RemovalReason reason, CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        WindowJournal.entityRemoving((Entity) (Object) this, reason);
    }
}
