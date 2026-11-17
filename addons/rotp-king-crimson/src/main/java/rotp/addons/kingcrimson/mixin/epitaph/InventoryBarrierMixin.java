package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Inventory;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** The inventory tick of an item can write to the world: a human phantom's items are not ticked. */
@Mixin(value = Inventory.class, priority = 900)
public abstract class InventoryBarrierMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomTick(CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            ci.cancel();
        }
    }
}
