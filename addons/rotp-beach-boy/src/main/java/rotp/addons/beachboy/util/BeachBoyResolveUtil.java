package rotp.addons.beachboy.util;

import javax.annotation.Nullable;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 BeachBoyResolveUtil (from ResolveCounterSexPistolsMixin at resolveOnHurtEvent): a hit with the rod in the
 * main hand gives half its damage more Resolve; the line's crush (and cramming) twice. The whip, tear and heart
 * crush multipliers keyed on a source name 1.16 never used (those hits took the Stand's own source), so they stay off.
 */
public final class BeachBoyResolveUtil {
    private static final float ROD_FULL_STAND_COMPENSATION = 0.5F;
    private static final float CRUSH_RESOLVE_MULTIPLIER = 2.0F;

    private BeachBoyResolveUtil() {}

    public static void addBeachBoyResolveBonus(@Nullable DamageSource source, @Nullable LivingEntity target, float damage) {
        if (source == null || target == null || target.level().isClientSide() || damage <= 0.0F || !target.isAlive()) {
            return;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && target.is(attacker)) {
            return;
        }
        StandPower power = getBeachBoyPower(source);
        if (power == null) {
            return;
        }
        float extra = 0.0F;
        if (isRodMelee(source, attacker)) {
            extra += damage * ROD_FULL_STAND_COMPENSATION;
        }
        if (source.is(BeachBoyAddon.CRUSH_DAMAGE_TYPE) || source.is(DamageTypes.CRAMMING)) {
            extra += damage * (CRUSH_RESOLVE_MULTIPLIER - 1.0F);
        }
        if (extra > 0.0F) {
            ResolveCounter.addResolve(power, target, extra);
        }
    }

    private static boolean isRodMelee(DamageSource source, @Nullable Entity attacker) {
        return standPowerOf(source) == null && attacker instanceof LivingEntity living
                && BeachBoyStandUtil.isRodItem(living.getMainHandItem());
    }

    @Nullable
    private static StandPower standPowerOf(DamageSource source) {
        return source instanceof DamageSourceModified modified ? modified.jojo_ripples$standPower() : null;
    }

    @Nullable
    private static StandPower getBeachBoyPower(DamageSource source) {
        StandPower power = standPowerOf(source);
        if (BeachBoySettings.isBeachBoy(power)) {
            return power;
        }
        if (source.getEntity() instanceof LivingEntity living) {
            StandPower attackerPower = StandPower.get(living);
            return BeachBoySettings.isBeachBoy(attackerPower) ? attackerPower : null;
        }
        return null;
    }
}
