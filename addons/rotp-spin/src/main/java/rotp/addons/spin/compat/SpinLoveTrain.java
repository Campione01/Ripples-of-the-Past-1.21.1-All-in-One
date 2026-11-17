package rotp.addons.spin.compat;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.fml.ModList;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.breaker.BallBreakerAddon;
import rotp.addons.spin.breaker.BallBreakerEntity;
import rotp.addons.spin.breaker.BallBreakerSenescence;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.entity.SteelBallEntity;

public final class SpinLoveTrain {
    private static final String D4C_ID = "rotp_d4c";
    private static final boolean D4C_LOADED = ModList.get().isLoaded(D4C_ID);
    private static final ResourceKey<MobEffect> LOVE_TRAIN =
            ResourceKey.create(Registries.MOB_EFFECT, ResourceLocation.fromNamespaceAndPath(D4C_ID, "love_train"));
    private static final ThreadLocal<DamagePermit> DAMAGE_PERMIT = new ThreadLocal<>();

    private SpinLoveTrain() {}

    public static boolean qualifiedBallHit(SteelBallEntity ball, LivingEntity owner, LivingEntity target,
            BooleanSupplier hit) {
        if (!supported() || !BallBreakerAddon.carriesPayload(ball, owner)) {
            return hit.getAsBoolean();
        }
        return withDamagePermit(target, source -> source.is(DamageTypes.ARROW)
                && source.getDirectEntity() == ball && source.getEntity() == owner, hit);
    }

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        if (!supported()) {
            return target.hurt(source, amount);
        }
        boolean touch = source.is(BallBreakerSenescence.TOUCH_DAMAGE)
                && source.getDirectEntity() instanceof BallBreakerEntity;
        boolean senescenceTick = source == target.damageSources().wither()
                && retained(target.getEffect(SpinAddon.SENESCENCE));
        return touch || senescenceTick
                ? withDamagePermit(target, candidate -> candidate == source, () -> target.hurt(source, amount))
                : target.hurt(source, amount);
    }

    private static boolean withDamagePermit(LivingEntity target, Predicate<DamageSource> source, BooleanSupplier hit) {
        DamagePermit previous = DAMAGE_PERMIT.get();
        DAMAGE_PERMIT.set(new DamagePermit(target, source));
        try {
            return hit.getAsBoolean();
        } finally {
            if (previous == null) {
                DAMAGE_PERMIT.remove();
            } else {
                DAMAGE_PERMIT.set(previous);
            }
        }
    }

    public static boolean consumeDamagePermit(LivingEntity target, DamageSource source) {
        DamagePermit permit = DAMAGE_PERMIT.get();
        if (permit == null) {
            DAMAGE_PERMIT.remove();
            return false;
        }
        if (permit.used || permit.target != target || !permit.source.test(source)) {
            return false;
        }
        permit.used = true;
        return true;
    }

    public static boolean addEffect(LivingEntity target, MobEffectInstance effect) {
        if (!supported() || !isBallBreakerEffect(effect)) {
            return target.addEffect(effect);
        }
        EffectProvenance provenance = provenance(effect);
        if (provenance == null) {
            return target.addEffect(effect);
        }
        // Senescence remains a Ball Breaker effect even if its victim activates Love Train later.
        provenance.rotp_spin$setRetained(effect.is(SpinAddon.SENESCENCE));
        LivingEntity previous = provenance.rotp_spin$applicationTarget();
        provenance.rotp_spin$setApplicationTarget(target);
        try {
            return target.addEffect(effect);
        } finally {
            provenance.rotp_spin$setApplicationTarget(previous);
        }
    }

    public static boolean allowEffectApplication(LivingEntity target, MobEffectInstance effect) {
        EffectProvenance provenance = provenance(effect);
        if (provenance == null || provenance.rotp_spin$applicationTarget() != target || !isBallBreakerEffect(effect)) {
            return false;
        }
        // The D4C handler already determined that this target has Love Train.
        provenance.rotp_spin$setRetained(true);
        return true;
    }

    public static boolean retainDuringPurge(LivingEntity target, @Nullable MobEffectInstance effect) {
        return effect != null && isBallBreakerEffect(effect) && retained(effect)
                && supported() && BuiltInRegistries.MOB_EFFECT.getHolder(LOVE_TRAIN).map(target::hasEffect).orElse(false);
    }

    public static boolean supported() {
        return D4C_LOADED && SpinConfig.COMPAT_D4C_ENABLED.get();
    }

    /** D4C destroys foreign projectiles near a Love Train user before they can hit; a ball that carries the payload goes on. */
    public static boolean passesInterception(Projectile projectile) {
        return supported() && projectile instanceof SteelBallEntity ball && ball.isSpinning()
                && ball.getOwner() instanceof LivingEntity owner && BallBreakerAddon.carriesPayload(ball, owner);
    }

    private static boolean isBallBreakerEffect(MobEffectInstance effect) {
        return effect.is(SpinAddon.SENESCENCE) || effect.is(MobEffects.MOVEMENT_SLOWDOWN);
    }

    public static boolean retained(@Nullable MobEffectInstance effect) {
        EffectProvenance provenance = provenance(effect);
        return provenance != null && provenance.rotp_spin$retained();
    }

    @Nullable
    private static EffectProvenance provenance(@Nullable MobEffectInstance effect) {
        return (Object) effect instanceof EffectProvenance provenance ? provenance : null;
    }

    public interface EffectProvenance {
        boolean rotp_spin$retained();
        void rotp_spin$setRetained(boolean retained);
        @Nullable LivingEntity rotp_spin$applicationTarget();
        void rotp_spin$setApplicationTarget(@Nullable LivingEntity target);
    }

    private static final class DamagePermit {
        private final LivingEntity target;
        private final Predicate<DamageSource> source;
        private boolean used;

        private DamagePermit(LivingEntity target, Predicate<DamageSource> source) {
            this.target = target;
            this.source = source;
        }
    }
}
