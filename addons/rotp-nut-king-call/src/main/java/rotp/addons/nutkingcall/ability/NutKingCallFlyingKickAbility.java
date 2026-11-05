package rotp.addons.nutkingcall.ability;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.nutkingcall.entity.NutKingCallEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 * 1.16 NutKingCallFlyingKickAction: the Stand leaps from 2 blocks before its user along the look, 1 block a tick, kicks
 * everything around it and, on meeting a block, breaks the blocks before it.
 */
public final class NutKingCallFlyingKickAbility extends StandEntityAbility {
    public static final int PERFORM_TICKS = 3;
    public static final int RECOVERY_TICKS = 5;
    public static final float STAMINA_COST = 40.0F;
    public static final int COOLDOWN_TICKS = 100;

    public static final double START_OFFSET = 2.0D;
    public static final double DASH_STEP = 1.0D;
    public static final int DASH_TICKS = 4;
    public static final double HIT_RANGE = 2.5D;
    public static final float DAMAGE = 25.0F;
    public static final double KNOCKBACK = 2.0D;
    public static final double KNOCKBACK_UP = 0.5D;
    public static final int BREAK_RADIUS = 3;
    public static final double BREAK_DISTANCE = 4.5D;
    public static final float MAX_BLOCK_HARDNESS = 5.0F;
    public static final float BREAK_CHANCE_CENTER = 1.0F;
    public static final float BREAK_CHANCE_EDGE = 0.3F;

    // 1.16 getStandPose: new StandPose("new"), the model's "new" clip
    private static final ActionAnimIdentifier ANIM = ActionAnimIdentifier.getOrCreate("new", false);

    public NutKingCallFlyingKickAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, FlyingKickAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        cooldown(COOLDOWN_TICKS, 0);
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return STAMINA_COST;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return ANIM;
    }

    // 1.16 standMovesByItself and lockStandManualMovement: the Stand flies on its own
    @Override
    public boolean noAdheringToUserOffset(StandPower standPower, StandEntity standEntity) {
        return true;
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(StandEntity standEntity) {
        return true;
    }

    @Override
    public boolean lockStandManualMovement(StandPower standPower, StandEntity standEntity) {
        return true;
    }

    public static final class FlyingKickAction extends EntityActionInstance {
        private boolean launched;

        public FlyingKickAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 onTaskSet in the PERFORM phase: the Stand takes its user's rotation and appears before the user
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase != ActionPhase.PERFORM || launched || level().isClientSide()
                    || !(getPerformer() instanceof NutKingCallEntity stand)) {
                return;
            }
            launched = true;
            stand.setFlyingKickHit(false);
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            Vec3 look = user.getViewVector(1.0F).normalize();
            stand.setYRot(user.getYRot());
            stand.yRotO = user.yRotO;
            stand.setXRot(user.getXRot());
            stand.xRotO = user.xRotO;
            stand.yHeadRot = user.yHeadRot;
            stand.yHeadRotO = user.yHeadRotO;
            stand.yBodyRot = user.yBodyRot;
            stand.yBodyRotO = user.yBodyRotO;
            stand.teleportTo(
                    user.getX() + look.x * START_OFFSET,
                    user.getY() + user.getBbHeight() * 0.5D + look.y * START_OFFSET,
                    user.getZ() + look.z * START_OFFSET);
            stand.setDeltaMovement(Vec3.ZERO);
            stand.hurtMarked = true;
        }

        // 1.16 StandEntityTask ran standPerform before standTickPerform in the perform's first tick
        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM || level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (getPhaseTick() < 1 && user != null) {
                dealDamage(level(), stand, user);
                // 1.16 StandEntityTask paid the staminaCost after standPerform
                StandAbilityStamina.consume(ability, StandPower.get(user), STAMINA_COST, false);
            }
            tickDash(stand, user);
        }

        private void tickDash(StandEntity standEntity, LivingEntity user) {
            if (!(standEntity instanceof NutKingCallEntity stand) || stand.isFlyingKickHit()
                    || user == null || getPhaseTick() > DASH_TICKS) {
                return;
            }
            Level level = level();
            Vec3 look = user.getViewVector(1.0F).normalize();
            Vec3 current = stand.position();
            Vec3 next = current.add(look.scale(DASH_STEP));
            BlockHitResult hit = level.clip(new ClipContext(
                    current, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, stand));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 stopPos = hit.getLocation().subtract(look.scale(0.1D));
                stand.teleportTo(stopPos.x, stopPos.y, stopPos.z);
                stand.setDeltaMovement(Vec3.ZERO);
                stand.hurtMarked = true;
                stand.setFlyingKickHit(true);
                breakBlocksAt(level, stand, hit.getBlockPos(), look);
                dealDamage(level, stand, user);
            }
            else {
                stand.teleportTo(next.x, next.y, next.z);
                stand.setDeltaMovement(Vec3.ZERO);
                stand.hurtMarked = true;
            }
        }

        private static void dealDamage(Level level, StandEntity stand, LivingEntity user) {
            AABB area = stand.getBoundingBox().inflate(HIT_RANGE);
            for (Entity entity : level.getEntities(stand, area,
                    e -> e instanceof LivingEntity && e != user && e != stand && e.isAlive())) {
                LivingEntity target = (LivingEntity) entity;
                target.hurt(stand.damageSources().mobAttack(stand), DAMAGE);
                Vec3 knockback = target.position().subtract(stand.position()).normalize().scale(KNOCKBACK);
                target.push(knockback.x, KNOCKBACK_UP, knockback.z);
                target.hurtMarked = true;
            }
        }

        // 1.16: blocks of hardness 0-5 within 4.5 blocks of the Stand and not behind it, broken at a chance falling
        // from 1 at the Stand to 0.3 at the edge; each block also asks the core's rule (the block-breaking game rule,
        // protection and the user's right to interact), which 1.16 did not have
        private static void breakBlocksAt(Level level, StandEntity stand, BlockPos hitPos, Vec3 look) {
            if (!(level instanceof ServerLevel serverLevel) || !JojoModUtil.breakingBlocksEnabled(serverLevel)) {
                return;
            }
            Vec3 center = stand.position();
            for (int x = -BREAK_RADIUS; x <= BREAK_RADIUS; x++) {
                for (int y = -BREAK_RADIUS; y <= BREAK_RADIUS; y++) {
                    for (int z = -BREAK_RADIUS; z <= BREAK_RADIUS; z++) {
                        BlockPos pos = hitPos.offset(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir()) {
                            continue;
                        }
                        float hardness = state.getDestroySpeed(level, pos);
                        if (hardness < 0.0F || hardness > MAX_BLOCK_HARDNESS) {
                            continue;
                        }
                        Vec3 toBlock = new Vec3(
                                pos.getX() + 0.5D - center.x,
                                pos.getY() + 0.5D - center.y,
                                pos.getZ() + 0.5D - center.z);
                        double dist = toBlock.length();
                        if (dist > BREAK_DISTANCE || dist > 0.001D && toBlock.normalize().dot(look) < 0.0D) {
                            continue;
                        }
                        float t = (float) Math.min(dist / BREAK_DISTANCE, 1.0D);
                        float chance = BREAK_CHANCE_CENTER + (BREAK_CHANCE_EDGE - BREAK_CHANCE_CENTER) * t;
                        if (level.random.nextFloat() < chance
                                && JojoModUtil.canEntityDestroy(serverLevel, pos, state, stand)) {
                            level.destroyBlock(pos, true);
                        }
                    }
                }
            }
        }
    }
}
