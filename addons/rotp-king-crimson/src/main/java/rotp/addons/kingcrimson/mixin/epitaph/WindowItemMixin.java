package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.item.ItemEntity;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/** An item a participant of a window dropped merges with no other: merged, what it held would lie in a stack nobody made. */
@Mixin(ItemEntity.class)
public abstract class WindowItemMixin {
    @Inject(method = "isMergable", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$windowMergable(CallbackInfoReturnable<Boolean> cir) {
        if (!WindowJournal.active) {
            return;
        }
        if (WindowJournal.walls((ItemEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
