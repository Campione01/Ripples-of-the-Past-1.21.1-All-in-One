package rotp.addons.gratefuldead.service;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.core.powersystem.standpower.StandDamageContext;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
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

    /** Old age dealt by the Grateful Dead user the aging is owned by: it takes the Stand's damage share. */
    public static void hurt(LivingEntity entity, float amount, @Nullable UUID owner) {
        if (entity == null || amount <= 0.0F || !entity.isAlive()
                || entity.level().isClientSide()) {
            return;
        }
        entity.invulnerableTime = 0;
        try (var running = StandDamageContext.enter(ownersStand(entity, owner))) {
            entity.hurt(entity.damageSources().source(AGING), amount);
        }
    }

    // an owner who is offline or no longer a Grateful Dead user leaves the damage as it is
    @Nullable
    private static StandPower ownersStand(LivingEntity entity, @Nullable UUID owner) {
        if (owner == null || entity.getServer() == null) {
            return null;
        }
        ServerPlayer player = entity.getServer().getPlayerList().getPlayer(owner);
        return player != null && AgingService.hasGratefulDead(player) ? StandPower.get(player) : null;
    }
}
