package rotp.addons.spin.mixin;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rotp.addons.spin.compat.SpinLoveTrain;

@Mixin(MobEffectInstance.class)
abstract class SpinLoveTrainEffectInstanceMixin implements SpinLoveTrain.EffectProvenance {
    @Unique private static final String ROTP_SPIN_RETAINED = "rotp_spin_love_train_retained";
    @Unique private boolean rotp_spin$retained;
    @Unique private @Nullable LivingEntity rotp_spin$applicationTarget;

    @Override
    public boolean rotp_spin$retained() {
        return rotp_spin$retained;
    }

    @Override
    public void rotp_spin$setRetained(boolean retained) {
        this.rotp_spin$retained = retained;
    }

    @Override
    public @Nullable LivingEntity rotp_spin$applicationTarget() {
        return rotp_spin$applicationTarget;
    }

    @Override
    public void rotp_spin$setApplicationTarget(@Nullable LivingEntity target) {
        this.rotp_spin$applicationTarget = target;
    }

    // Only the two foreground duration writes replace ownership. A hidden or cosmetic update does not.
    @Inject(method = "update(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/effect/MobEffectInstance;duration:I",
                    opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
            require = 0, expect = 2)
    private void rotp_spin$takeForegroundProvenance(MobEffectInstance incoming, CallbackInfoReturnable<Boolean> cir) {
        rotp_spin$retained = SpinLoveTrain.retained(incoming);
    }

    @Inject(method = "setDetailsFrom(Lnet/minecraft/world/effect/MobEffectInstance;)V", at = @At("RETURN"))
    private void rotp_spin$copyProvenance(MobEffectInstance incoming, CallbackInfo ci) {
        rotp_spin$retained = SpinLoveTrain.retained(incoming);
    }

    @Inject(method = "save()Lnet/minecraft/nbt/Tag;", at = @At("RETURN"))
    private void rotp_spin$saveProvenance(CallbackInfoReturnable<Tag> cir) {
        MobEffectInstance effect = (MobEffectInstance) (Object) this;
        if (cir.getReturnValue() instanceof CompoundTag data) {
            while (effect != null) {
                if (SpinLoveTrain.retained(effect)) {
                    data.putBoolean(ROTP_SPIN_RETAINED, true);
                }
                effect = effect.hiddenEffect;
                if (!data.contains("hidden_effect", Tag.TAG_COMPOUND)) {
                    break;
                }
                data = data.getCompound("hidden_effect");
            }
        }
    }

    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/effect/MobEffectInstance;", at = @At("RETURN"))
    private static void rotp_spin$loadProvenance(CompoundTag data, CallbackInfoReturnable<MobEffectInstance> cir) {
        MobEffectInstance effect = cir.getReturnValue();
        while (effect != null) {
            ((SpinLoveTrain.EffectProvenance) (Object) effect).rotp_spin$setRetained(data.getBoolean(ROTP_SPIN_RETAINED));
            effect = effect.hiddenEffect;
            if (!data.contains("hidden_effect", Tag.TAG_COMPOUND)) {
                break;
            }
            data = data.getCompound("hidden_effect");
        }
    }
}
