package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/** An arrow or a trident a participant of a window shot is collected by him alone: a trident asks its arrow part. */
@Mixin(AbstractArrow.class)
public abstract class WindowArrowMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$windowKeepsShot(Player player, CallbackInfo ci) {
        if (!WindowJournal.active) {
            return;
        }
        if (WindowJournal.keeps((Entity) (Object) this, player)) {
            ci.cancel();
        }
    }
}
