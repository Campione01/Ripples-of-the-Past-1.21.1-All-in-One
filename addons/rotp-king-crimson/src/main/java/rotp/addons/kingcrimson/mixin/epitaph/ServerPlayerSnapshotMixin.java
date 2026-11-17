package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.sandbox.PhantomFactory;

/**
 * The saved data a copy of a player is made from leaves the recipe book out. And the being of a walked player lives at
 * the pace of his track: on a server tick in which his track passed no whole tick, the tick of his being is left out
 * (hunger, air, fire, his effects, his cooldowns, the tick event of a player); what his connection does for him each
 * tick is his own.
 */
@Mixin(value = ServerPlayer.class, priority = 900)
public abstract class ServerPlayerSnapshotMixin {
    @WrapOperation(method = "addAdditionalSaveData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/stats/ServerRecipeBook;toNbt()Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag kingCrimson$leaveRecipeBookOut(ServerRecipeBook book, Operation<CompoundTag> original) {
        return PhantomFactory.skippingRecipeBook ? new CompoundTag() : original.call(book);
    }

    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$beingAtThePaceOfItsTrack(CallbackInfo ci) {
        if (!EpitaphSessions.any) {
            return;
        }
        if (EpitaphSessions.holdsBeing((ServerPlayer) (Object) this)) {
            ci.cancel();
        }
    }
}
