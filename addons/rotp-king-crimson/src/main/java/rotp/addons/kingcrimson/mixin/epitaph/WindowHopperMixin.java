package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;

/** A hopper takes no item a participant of a window dropped: in a container it could not be found again. */
@Mixin(HopperBlockEntity.class)
public abstract class WindowHopperMixin {
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void kingCrimson$windowAddItem(Container container, ItemEntity item, CallbackInfoReturnable<Boolean> cir) {
        if (!WindowJournal.active) {
            return;
        }
        if (WindowJournal.walls(item)) {
            cir.setReturnValue(false);
        }
    }
}
