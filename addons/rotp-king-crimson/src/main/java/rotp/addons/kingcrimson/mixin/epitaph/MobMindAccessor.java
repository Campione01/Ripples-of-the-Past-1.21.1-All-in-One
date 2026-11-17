package rotp.addons.kingcrimson.mixin.epitaph;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** A copy takes its target as a field: setTarget would post the target change event for an entity outside the world. */
@Mixin(Mob.class)
public interface MobMindAccessor {
    @Accessor("target")
    void kingCrimson$setTargetField(@Nullable LivingEntity target);
}
