package rotp.addons.kingcrimson.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import rotp.addons.kingcrimson.state.KingCrimsonShiftedImages;

/**
 * On a Time Erase user's client a shifted player's entity goes to his image, whatever position a packet gives for him
 * (owner 2026-11-07, the shifted image). Elsewhere KingCrimsonShiftedImages is empty and this is one read.
 */
@Mixin(LivingEntity.class)
public abstract class TimeEraseShiftLerpMixin {
    @ModifyVariable(method = "lerpTo", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double kingCrimson$imageX(double x) {
        Vec3 image = kingCrimson$image();
        return image != null ? image.x : x;
    }

    @ModifyVariable(method = "lerpTo", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double kingCrimson$imageY(double y) {
        Vec3 image = kingCrimson$image();
        return image != null ? image.y : y;
    }

    @ModifyVariable(method = "lerpTo", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double kingCrimson$imageZ(double z) {
        Vec3 image = kingCrimson$image();
        return image != null ? image.z : z;
    }

    private Vec3 kingCrimson$image() {
        if (!KingCrimsonShiftedImages.any()) {
            return null;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        return self.level().isClientSide() ? KingCrimsonShiftedImages.imageOf(self.getId()) : null;
    }
}
