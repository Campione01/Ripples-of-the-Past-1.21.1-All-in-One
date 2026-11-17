package rotp.addons.spin.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import rotp.addons.spin.ability.SpinLessonAbilities;

@Mixin(Player.class)
abstract class SpinBracePlayerMixin {
    // The armor call is reached only after vanilla accepted the hit and processed shield and i-frames.
    @ModifyArg(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"),
            index = 1)
    private float rotpSpin$braceBeforeArmor(DamageSource source, float amount) {
        return SpinLessonAbilities.absorbBeforeArmor((Player) (Object) this, source, amount);
    }
}
