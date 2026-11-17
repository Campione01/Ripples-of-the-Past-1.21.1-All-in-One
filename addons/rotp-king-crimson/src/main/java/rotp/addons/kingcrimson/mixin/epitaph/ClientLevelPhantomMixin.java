package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import rotp.addons.kingcrimson.client.epitaph.EpitaphClientSession;
import rotp.addons.kingcrimson.client.epitaph.PhantomContext;

/**
 * The level of the client while the phantoms of the local player's prediction are handled: an entity a packet of a
 * frame makes goes to the phantoms and not into the level, a removal takes a phantom and never the entity of the
 * world that has its id, and lookups answer with the phantoms first. Dead while the flag of the client is false.
 */
@Mixin(value = ClientLevel.class, priority = 900)
public abstract class ClientLevelPhantomMixin {
    @Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$phantomAdd(Entity entity, CallbackInfo ci) {
        if (!PhantomContext.active) {
            return;
        }
        if (EpitaphClientSession.adopt(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "removeEntity(ILnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$phantomRemove(int entityId, Entity.RemovalReason reason, CallbackInfo ci) {
        if (!PhantomContext.active) {
            return;
        }
        EpitaphClientSession.discard(entityId);
        ci.cancel();
    }

    @Inject(method = "getEntities()Lnet/minecraft/world/level/entity/LevelEntityGetter;", at = @At("RETURN"), cancellable = true)
    private void kingCrimson$phantomEntities(CallbackInfoReturnable<LevelEntityGetter<Entity>> cir) {
        if (!PhantomContext.active) {
            return;
        }
        cir.setReturnValue(EpitaphClientSession.over(cir.getReturnValue()));
    }
}
