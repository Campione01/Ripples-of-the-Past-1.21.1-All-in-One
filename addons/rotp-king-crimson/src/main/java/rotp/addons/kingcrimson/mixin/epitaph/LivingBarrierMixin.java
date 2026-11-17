package rotp.addons.kingcrimson.mixin.epitaph;

import java.util.List;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.replay.FutureDrops;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxDamage;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;

/**
 * The barrier on living entities. In a room a hit and a death are the room's own, with no event of a real one, and an
 * effect put on or taken off a phantom is recorded; nothing of the kind reaches an entity that is not of the room.
 * Outside a room one hook acts: a body that dies as it was foreseen to drops the loot that was foreseen.
 */
@Mixin(value = LivingEntity.class, priority = 900)
public abstract class LivingBarrierMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(SandboxDamage.hurt((LivingEntity) (Object) this, source, amount));
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(EpitaphSandbox.mirror().lands((LivingEntity) (Object) this, source));
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomDie(DamageSource source, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            SandboxDamage.die(EpitaphSandbox.room(), (LivingEntity) (Object) this, source);
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomGuardEffect(MobEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed() && EpitaphSandbox.room().isReal((Entity) (Object) this)) {
            EpitaphSandbox.leak("room code put an effect on a real entity");
            cir.setReturnValue(false);
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"))
    private void kingCrimson$roomRecordEffect(MobEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed() && cir.getReturnValue() && EpitaphSandbox.room().owns((Entity) (Object) this)) {
            EpitaphSandbox.record(new RecordedOp.Effect(EpitaphSandbox.actorKey(), ActorKey.of((Entity) (Object) this),
                    new MobEffectInstance(effect)));
        }
    }

    @Inject(method = "removeEffect", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomGuardRemoveEffect(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed() && EpitaphSandbox.room().isReal((Entity) (Object) this)) {
            EpitaphSandbox.leak("room code took an effect off a real entity");
            cir.setReturnValue(false);
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "removeEffect", at = @At("RETURN"))
    private void kingCrimson$roomRecordRemoveEffect(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed() && cir.getReturnValue() && EpitaphSandbox.room().owns((Entity) (Object) this)) {
            EpitaphSandbox.record(new RecordedOp.EffectRemoved(EpitaphSandbox.actorKey(), ActorKey.of((Entity) (Object) this),
                    effect));
        }
    }

    @Inject(method = "removeAllEffects", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomRemoveAllEffects(CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            LivingEntity self = (LivingEntity) (Object) this;
            SandboxRoom room = EpitaphSandbox.room();
            if (room.isReal(self)) {
                EpitaphSandbox.leak("room code cleared the effects of a real entity");
                cir.setReturnValue(false);
                return;
            }
            if (!room.owns(self)) {
                return;
            }
            for (MobEffectInstance effect : List.copyOf(self.getActiveEffects())) {
                room.record(new RecordedOp.EffectRemoved(EpitaphSandbox.actorKey(), ActorKey.of(self), effect.getEffect()));
            }
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$dropForeseenLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (!EpitaphSessions.any) {
            return;
        }
        EpitaphSandbox.countPass();
        if (FutureDrops.drop((LivingEntity) (Object) this, level, source)) {
            ci.cancel();
        }
    }
}
