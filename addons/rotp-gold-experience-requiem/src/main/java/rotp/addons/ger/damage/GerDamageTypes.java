package rotp.addons.ger.damage;

import javax.annotation.Nullable;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Requiem's own damage types (data/geraddon/damage_type). Each carries the
 * tags that keep a hit from being avoided: vanilla's for the hurt cooldown,
 * shields, invulnerable flags and Resistance, and the core's for its own
 * guard, parry, invulnerable states and caps.
 */
public final class GerDamageTypes {
    /** The Stand's punches, barrage, heavy and charged heavy punch. */
    public static final ResourceKey<DamageType> REQUIEM_ATTACK =
            key("requiem_attack");
    /** The heavy punch's block blast. */
    public static final ResourceKey<DamageType> REQUIEM_EXPLOSION =
            key("requiem_explosion");
    public static final ResourceKey<DamageType> REQUIEM_LIFE_SHOT =
            key("requiem_life_shot");
    /** The Finisher Punch's own hit: armour and enchantments do not reduce it. */
    public static final ResourceKey<DamageType> REQUIEM_TRUE =
            key("requiem_true");
    /** Every death of the loop after the first. */
    public static final ResourceKey<DamageType> DEATH_LOOP =
            key("death_loop");
    /** Every type above. */
    public static final TagKey<DamageType> REQUIEM = TagKey.create(
            Registries.DAMAGE_TYPE,
            GoldExperienceRequiemAddon.id("requiem"));

    private GerDamageTypes() {}

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(
                Registries.DAMAGE_TYPE,
                GoldExperienceRequiemAddon.id(path));
    }

    /** Requiem's type for a hit the core made with one of its own Stand types; any other source as it is. */
    public static DamageSource requiemOf(Level level, DamageSource source) {
        if (source.is(ModDamageTypes.STAND_ATTACK)) {
            return retyped(level, source, REQUIEM_ATTACK);
        }
        if (source.is(ModDamageTypes.STAND_EXPLOSION)) {
            return retyped(level, source, REQUIEM_EXPLOSION);
        }
        return source;
    }

    /** The same hit, from the same entities and with the core's knockback and Stand data, under another type. */
    public static DamageSource retyped(
            Level level, DamageSource source, ResourceKey<DamageType> type) {
        DamageSource retyped = new DamageSource(
                DamageUtil.type(level, type),
                source.getDirectEntity(),
                source.getEntity(),
                source.sourcePositionRaw());
        if (source instanceof DamageSourceModified from
                && retyped instanceof DamageSourceModified to) {
            to.jojo_ripples$modifyKnockback(
                    from.jojo_ripples$addKnockback(),
                    from.jojo_ripples$knockbackMultiplier());
            to.jojo_ripples$verticalKnockback(
                    from.jojo_ripples$verticalKnockbackStrength(),
                    from.jojo_ripples$verticalKnockbackAngleRatio());
            to.jojo_ripples$knockbackXRot(
                    from.jojo_ripples$knockbackXRotDeg());
            to.jojo_ripples$knockbackYRot(
                    from.jojo_ripples$knockbackYRotDeg());
            to.jojo_ripples$setStandInvulTicks(
                    from.jojo_ripples$standInvulTicks());
            to.jojo_ripples$setBarrageHitsCount(
                    from.jojo_ripples$barrageHitsCount());
            to.jojo_ripples$setStandPower(
                    from.jojo_ripples$standPower());
            to.jojo_ripples$setNonLethal(
                    from.jojo_ripples$isNonLethal());
            to.jojo_ripples$setBreakStandGuard(
                    from.jojo_ripples$breaksStandGuard());
            to.jojo_ripples$setFixedDamage(
                    from.jojo_ripples$isFixedDamage());
        }
        return retyped;
    }

    /** What the hit changed on the source the target took goes back to the one its caller reads. */
    public static void copyBack(DamageSource dealt, DamageSource callers) {
        if (dealt != callers
                && dealt instanceof DamageSourceModified from
                && callers instanceof DamageSourceModified to) {
            to.jojo_ripples$setBarrageHitsCount(
                    from.jojo_ripples$barrageHitsCount());
        }
    }

    /** A source of one of Requiem's types, which knows the Stand power behind it as the core's Stand hits do. */
    public static DamageSource source(
            Level level,
            ResourceKey<DamageType> type,
            @Nullable Entity direct,
            @Nullable LivingEntity user) {
        DamageSource source = new DamageSource(
                DamageUtil.type(level, type), direct, user);
        if (user != null && source instanceof DamageSourceModified modified) {
            modified.jojo_ripples$setStandPower(StandPower.get(user));
        }
        return source;
    }
}
