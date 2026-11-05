package rotp.addons.gratefuldead.service;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Next Album 0.1.13 AgingDamageSource "rotp_nextalbum.aging": old age,
 * through armor and enchantments (data/rotp_nextalbum/damage_type/aging.json
 * and the minecraft bypass tags).
 */
public final class AgingDamage {
    public static final ResourceKey<DamageType> AGING = ResourceKey.create(
            Registries.DAMAGE_TYPE, GratefulDeadAddon.id("aging"));

    private AgingDamage() {}

    public static void hurt(LivingEntity entity, float amount) {
        if (entity == null || amount <= 0.0F || !entity.isAlive()
                || entity.level().isClientSide()) {
            return;
        }
        entity.invulnerableTime = 0;
        entity.hurt(entity.damageSources().source(AGING), amount);
    }
}
