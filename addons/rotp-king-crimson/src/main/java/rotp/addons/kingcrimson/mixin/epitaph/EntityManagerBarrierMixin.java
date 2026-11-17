package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/** One call below the level's entity add: the belt. Nothing made in a room is registered in the world. */
@Mixin(value = PersistentEntitySectionManager.class, priority = 900)
public abstract class EntityManagerBarrierMixin {
    @Inject(method = "addNewEntity", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomAddNewEntity(EntityAccess entity, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.leak("room code registered an entity in the world");
            cir.setReturnValue(false);
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(false);
        }
    }
}
