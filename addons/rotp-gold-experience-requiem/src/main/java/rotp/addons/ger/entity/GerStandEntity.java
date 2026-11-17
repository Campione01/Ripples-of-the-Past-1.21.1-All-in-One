package rotp.addons.ger.entity;

import rotp.core.JojoModConfig;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;

import rotp.addons.ger.damage.GerDamageTypes;
import rotp.addons.ger.damage.GerUnavoidableDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GerStandEntity extends StandEntity {
    private static final float EYE_HEIGHT_ABOVE_STANDING_EYE = 0.7F;

    public GerStandEntity(
            EntityType<? extends GerStandEntity> entityType,
            Level level) {
        super(entityType, level);
        // 1.16 GoldExprienceRequiemEntity: behind the user's left shoulder,
        // the mirror of the core default.
        offsetFromUser.idleOffset = new Vec3(0.75D, Y_OFFSET, -0.75D);
        offsetFromUser.idleRotations = StandOffsetFromUser.Rotations.HEAD;
        offsetFromUser.resetToIdle();
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions dimensions = super.getDefaultDimensions(pose);
        return dimensions.withEyeHeight(
                dimensions.eyeHeight() + EYE_HEIGHT_ABOVE_STANDING_EYE);
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    /**
     * Every punch, barrage tick, heavy and charged heavy punch and the heavy
     * punch's block blast pass here: they land as Requiem's own damage types,
     * which nothing avoids (GerUnavoidableDamage).
     */
    @Override
    public boolean hurtWithStandAttack(
            Entity target, DamageSource source, float amount) {
        if (level().isClientSide()
                || GerUnavoidableDamage.staysOut(target)) {
            return super.hurtWithStandAttack(target, source, amount);
        }
        DamageSource requiem =
                GerDamageTypes.requiemOf(level(), source);
        float dealt = amount * damageMultiplier(requiem);
        boolean hurt = GerUnavoidableDamage.dealing(
                target,
                requiem,
                dealt,
                () -> target instanceof LivingEntity living
                                && isStandAttackInvulnerable(living)
                        ? hurtThroughStandInvulnerability(
                                living, requiem, dealt)
                        : super.hurtWithStandAttack(
                                target, requiem, amount));
        GerDamageTypes.copyBack(requiem, source);
        return hurt;
    }

    private static float damageMultiplier(DamageSource source) {
        return source instanceof DamageSourceModified modified
                        && modified.jojo_ripples$isFixedDamage()
                ? 1.0F
                : JojoModConfig.getCommonConfigInstance(false)
                        .standDamageMultiplier.get().floatValue();
    }

    // The core cuts a Stand hit inside the frames its last heavy hit left to nothing; what it does besides stays.
    private static boolean hurtThroughStandInvulnerability(
            LivingEntity target, DamageSource source, float amount) {
        if (source instanceof DamageSourceModified modified) {
            if (target instanceof StandEntity targetStand
                    && modified.jojo_ripples$breaksStandGuard()) {
                targetStand.breakStandBlocking(
                        StandStatFormulas.getGuardBreakTicks(
                                targetStand.getDurability()));
            }
            if (modified.jojo_ripples$isNonLethal()) {
                amount = Math.min(
                        amount,
                        Math.max(target.getHealth() - 0.0001F, 0.0F));
            }
        }
        return GerUnavoidableDamage.deal(target, source, amount);
    }
}
