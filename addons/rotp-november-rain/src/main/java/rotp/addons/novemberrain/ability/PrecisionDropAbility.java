package rotp.addons.novemberrain.ability;

import java.util.List;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.novemberrain.NovemberRainAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The remake's precision_drop (PrecisionDrop): staminaCost(50), resolveLevelToUnlock(2), cooldown(50), aimed at an entity
 * or a block (TargetRequirement.ANY), the idle pose. One drop falls on a living target within 10 blocks of the Stand,
 * or on whoever stands under the block the Stand looks at within 10 blocks, if the space below it is air (and the block
 * breaks when it hits someone, where the server lets the Stand break it: the core's block-breaking game rule and
 * JojoModUtil.canEntityDestroy, which the remake did not ask): 15 anvil-type damage and Cut Eyelid for 5 seconds. The
 * remake registered it on no hotbar and left its igniteTNT helper unused.
 */
public final class PrecisionDropAbility extends NovemberRainStandAbility {
    public static final float DAMAGE = 15.0F;
    public static final double RANGE = 10.0D;
    public static final float STAMINA_COST = 50.0F;
    public static final int COOLDOWN = 50;
    public static final int RESOLVE_LEVEL = 2;
    public static final int CUT_EYELID_TICKS = 100;
    public static final int BLOCK_DRIP_PARTICLES = 8;

    public PrecisionDropAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, new Spec()
                .staminaCost(STAMINA_COST)
                .resolveLevelToUnlock(RESOLVE_LEVEL)
                .cooldown(COOLDOWN)
                .targetRequirement(TargetRequirement.ANY));
    }

    // 1.16 preTaskInit; its particles were the presser's own client's, here the server sends them to all near
    @Override
    protected void standPerform(NovemberRainAction action, Level level, StandEntity stand, LivingEntity user) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ActionTarget target = action.getPressedTarget();
        Entity entity = target.getType() == ActionTarget.TargetType.ENTITY ? target.getEntity() : null;
        if (entity instanceof LivingEntity targetEntity) {
            if (targetEntity.isAlive() && stand.distanceTo(targetEntity) <= RANGE) {
                Vec3 position = targetEntity.position().add(0.0D, 8.0D, 0.0D);
                serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER,
                        position.x, position.y, position.z, 0, 0.0D, -1.0D, 0.0D, 1.0D);
                hitWithDrop(level, targetEntity);
            }
            return;
        }
        Vec3 start = stand.getEyePosition(1.0F);
        Vec3 direction = stand.getLookAngle();
        Vec3 end = start.add(direction.x * RANGE, direction.y * RANGE, direction.z * RANGE);
        BlockHitResult rayTrace = level.clip(
                new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, stand));
        if (rayTrace.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos blockPos = rayTrace.getBlockPos();
        if (!level.getBlockState(blockPos.below()).isAir()) {
            return;
        }
        for (int i = 0; i < BLOCK_DRIP_PARTICLES; i++) {
            serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER,
                    blockPos.getX() + 0.2D + level.random.nextDouble() * 0.6D,
                    blockPos.getY(),
                    blockPos.getZ() + 0.2D + level.random.nextDouble() * 0.6D,
                    0, 0.0D, -0.5D, 0.0D, 1.0D);
        }
        AABB area = new AABB(blockPos.getX(), blockPos.getY() - 1.0D, blockPos.getZ(),
                blockPos.getX() + 1.0D, blockPos.getY(), blockPos.getZ() + 1.0D);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area);
        for (LivingEntity livingEntity : entities) {
            hitWithDrop(level, livingEntity);
        }
        BlockState blockState = level.getBlockState(blockPos);
        if (!entities.isEmpty() && JojoModUtil.canEntityDestroy(serverLevel, blockPos, blockState, stand)) {
            level.destroyBlock(blockPos, false);
        }
    }

    private static void hitWithDrop(Level level, LivingEntity target) {
        target.hurt(level.damageSources().source(DamageTypes.FALLING_ANVIL), DAMAGE);
        target.addEffect(new MobEffectInstance(NovemberRainAddon.CUT_EYELID, CUT_EYELID_TICKS, 0));
    }
}
