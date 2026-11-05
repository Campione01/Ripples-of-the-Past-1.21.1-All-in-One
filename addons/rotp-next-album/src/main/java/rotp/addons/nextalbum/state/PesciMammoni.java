package rotp.addons.nextalbum.state;

import java.util.List;

import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem.Suit;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Next Album 0.1.13 PesciSuitUtil "Resolve of a Mammoni": with the full
 * Pesci suit the summoned Stand's stats are x0.8 at no Resolve, rising
 * linearly to x1.5 at full Resolve.
 */
public final class PesciMammoni {
    static final ResourceLocation MODIFIER =
            NextAlbumIds.id("pesci_mammoni");
    // 1.16 modified damage, speed, durability, precision, reach and
    // movement; reach is the Stand's entity and block interaction range.
    private static final List<Holder<Attribute>> STATS = List.of(
            Attributes.ATTACK_DAMAGE,
            Attributes.ATTACK_SPEED,
            ModEntityAttributes.STAND_DURABILITY,
            ModEntityAttributes.STAND_PRECISION,
            Attributes.ENTITY_INTERACTION_RANGE,
            Attributes.BLOCK_INTERACTION_RANGE,
            Attributes.MOVEMENT_SPEED);

    private PesciMammoni() {}

    public static float standStatMultiplier(LivingEntity user) {
        if (!CharacterSuitArmorItem.hasFullSet(user, Suit.PESCI)) {
            return 1.0F;
        }
        StandPower power = StandPower.get(user);
        if (power == null || !power.hasPower()) {
            return 0.8F;
        }
        float ratio = Mth.clamp(
                power.getResolveCounter().getResolveRatio(power),
                0.0F, 1.0F);
        return 0.8F + 0.7F * ratio;
    }

    public static void tick(LivingEntity user) {
        if (user.level().isClientSide()) {
            return;
        }
        StandPower power = StandPower.get(user);
        StandEntity stand = power != null && power.hasPower()
                ? power.getSummonedStandEntity()
                : null;
        if (stand == null) {
            return;
        }
        double amount = standStatMultiplier(user) - 1.0D;
        for (Holder<Attribute> attribute : STATS) {
            AttributeInstance instance = stand.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            AttributeModifier current = instance.getModifier(MODIFIER);
            if (Math.abs(amount) < 0.001D) {
                if (current != null) {
                    instance.removeModifier(MODIFIER);
                }
            } else if (current == null
                    || Math.abs(current.amount() - amount) > 1.0E-4D) {
                instance.addOrUpdateTransientModifier(new AttributeModifier(
                        MODIFIER, amount,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }
}
