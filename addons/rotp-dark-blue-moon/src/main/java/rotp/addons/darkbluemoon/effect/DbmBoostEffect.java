package rotp.addons.darkbluemoon.effect;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * 1.16 DarkBlueMoonWaterEffect and DarkBlueMoonRainEffect: added attack damage, movement speed and swim speed while
 * the effect lasts (water: user +3 / Stand +5 attack, +0.01 speed, +0.5 swim; rain: +1.5, +0.005, +0.25).
 */
public class DbmBoostEffect extends MobEffect {
    public static final int COLOR = 1981066;

    public DbmBoostEffect(String idPrefix, double attackBonus, double speedBonus, double swimBonus) {
        super(MobEffectCategory.BENEFICIAL, COLOR);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, DarkBlueMoonAddon.id(idPrefix + "_atk"),
                attackBonus, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, DarkBlueMoonAddon.id(idPrefix + "_spd"),
                speedBonus, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(NeoForgeMod.SWIM_SPEED, DarkBlueMoonAddon.id(idPrefix + "_swim"),
                swimBonus, AttributeModifier.Operation.ADD_VALUE);
    }
}
