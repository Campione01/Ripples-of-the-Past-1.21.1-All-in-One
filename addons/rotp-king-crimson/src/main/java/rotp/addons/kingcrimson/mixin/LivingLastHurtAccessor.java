package rotp.addons.kingcrimson.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.LivingEntity;

/** The damage of the hit whose invulnerability frames still run: King Crimson's passive reads what a new hit adds to it. */
@Mixin(LivingEntity.class)
public interface LivingLastHurtAccessor {
    @Accessor("lastHurt")
    float kingCrimson$lastHurt();
}
