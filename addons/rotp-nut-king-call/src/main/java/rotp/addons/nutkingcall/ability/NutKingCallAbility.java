package rotp.addons.nutkingcall.ability;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;

import rotp.addons.nutkingcall.NutKingCallService;
import rotp.addons.nutkingcall.block.NutKingCallNailBlock;
import rotp.addons.nutkingcall.entity.NutKingCallAttachedNailEntity;
import rotp.addons.nutkingcall.entity.NutKingCallEntity;
import rotp.addons.nutkingcall.entity.NutKingCallNailEntity;
import rotp.addons.nutkingcall.entity.NutKingCallNutEntity;
import rotp.addons.nutkingcall.entity.NutKingCallProjectile;
import rotp.addons.nutkingcall.state.NailState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** The Nut King Call Stand actions without a movement of their own, 1.16's StandEntityAction subclasses. */
public final class NutKingCallAbility extends StandEntityAbility {
    /** 1.16 InitStands: standPerformDuration, standRecoveryTicks, staminaCost and cooldown(n, 0) of each action. */
    public enum Mode {
        QUICK_ASSEMBLY(20, 20, 50.0F, 200),
        TOGGLE_MODE(5, 5, 10.0F, 20),
        FIRE_NAIL(10, 10, 25.0F, 20),
        DISMANTLE(20, 20, 30.0F, 60),
        GIVE_NAIL(10, 10, 20.0F, 40),
        PIN(10, 10, 30.0F, 40),
        UNPIN_TARGET(5, 5, 10.0F, 20),
        PIN_SELF(10, 10, 30.0F, 40),
        UNPIN_SELF(5, 5, 10.0F, 20);

        public final int performTicks;
        public final int recoveryTicks;
        public final float staminaCost;
        public final int cooldownTicks;

        Mode(int performTicks, int recoveryTicks, float staminaCost, int cooldownTicks) {
            this.performTicks = performTicks;
            this.recoveryTicks = recoveryTicks;
            this.staminaCost = staminaCost;
            this.cooldownTicks = cooldownTicks;
        }
    }

    public static final float FIRE_NAIL_SPEED = 1.5F;

    private final Mode mode;

    public NutKingCallAbility(AbilityType<?> abilityType, AbilityId abilityId, Mode mode) {
        super(abilityType, abilityId, actionType -> new NutKingCallAction(actionType, mode));
        this.mode = mode;
        setDefaultPhaseLength(ActionPhase.PERFORM, mode.performTicks);
        setDefaultPhaseLength(ActionPhase.RECOVERY, mode.recoveryTicks);
        cooldown(mode.cooldownTicks, 0);
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return mode.staminaCost;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        LivingEntity user = context != null ? context.getUser() : null;
        if (!check.isPositive() || user == null) {
            return check;
        }
        return switch (mode) {
            case PIN_SELF -> NailState.isPinned(user) ? ConditionCheck.NEGATIVE : ConditionCheck.POSITIVE;
            case UNPIN_TARGET -> NutKingCallService.findNearestPinnedTarget(user) == null
                    ? ConditionCheck.NEGATIVE : ConditionCheck.POSITIVE;
            case GIVE_NAIL, PIN -> checkPressedTarget(user);
            default -> ConditionCheck.POSITIVE;
        };
    }

    /*
     * 1.16 PowerBaseImpl.checkTarget for give_nail (TargetRequirement.ANY) and pin (ENTITY): a press at no target of the
     * kind was refused silently; one past 8 blocks of the performer (4 unseen), or a block past 10, with
     * "target_too_far"; then the action's checkTarget (no Stand; pin: a living entity, not an attached nail), an air
     * block, and while the Stand was out its checkStandTarget (a living entity it may attack).
     */
    private ConditionCheck checkPressedTarget(LivingEntity user) {
        ActionTarget target = overridePressedTarget(user, NutKingCallTargeting.pressedTarget(user));
        Level level = user.level();
        ActionTarget.TargetType type = target != null ? target.getType() : ActionTarget.TargetType.EMPTY;
        boolean typeAccepted = mode == Mode.PIN
                ? type == ActionTarget.TargetType.ENTITY
                : type == ActionTarget.TargetType.ENTITY || type == ActionTarget.TargetType.BLOCK;
        if (!typeAccepted || target.isEmpty(level)) {
            return ConditionCheck.NEGATIVE;
        }
        LivingEntity performer = ActionTargetRange.standPerformer(user);
        if (type == ActionTarget.TargetType.BLOCK) {
            BlockPos pos = target.getBlockPos();
            if (level.getBlockState(pos).isAir()) {
                return ConditionCheck.NEGATIVE;
            }
            if (!ActionTargetRange.isBlockWithinRange(
                    performer, level, pos, ActionTargetRange.DEFAULT_BLOCK_RANGE_SQ)) {
                return ConditionCheck.createNegative("target_too_far");
            }
            return ConditionCheck.POSITIVE;
        }
        Entity entity = target.getEntity();
        if (!ActionTargetRange.isEntityWithinRange(performer, entity, ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if (entity instanceof StandEntity
                || mode == Mode.PIN && (!(entity instanceof LivingEntity)
                        || entity instanceof NutKingCallAttachedNailEntity)) {
            return ConditionCheck.NEGATIVE;
        }
        if (performer instanceof StandEntity stand
                && !(entity instanceof LivingEntity living && stand.canAttackEntity(living))) {
            return ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
        return switch (mode) {
            case GIVE_NAIL -> target.getType() == ActionTarget.TargetType.ENTITY
                    || target.getType() == ActionTarget.TargetType.BLOCK;
            case PIN -> target.getType() == ActionTarget.TargetType.ENTITY;
            default -> false;
        };
    }

    @Override
    public String getSpriteName(Power<?> context) {
        if (mode == Mode.QUICK_ASSEMBLY && StandUtil.getSummonedStand(context) instanceof NutKingCallEntity stand) {
            return name() + (stand.isDisassembleMode() ? "_disassemble" : "_assemble");
        }
        return super.getSpriteName(context);
    }

    @Override
    public Component getName(Power<?> context) {
        if (mode == Mode.TOGGLE_MODE && StandUtil.getSummonedStand(context) instanceof NutKingCallEntity stand) {
            return abilityName(context, stand.isDisassembleMode() ? ".disassemble" : ".assemble");
        }
        return super.getName(context);
    }

    public static final class NutKingCallAction extends EntityActionInstance {
        private final Mode mode;

        public NutKingCallAction(EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if ((mode == Mode.GIVE_NAIL || mode == Mode.PIN) && getPerformer() instanceof StandEntity stand) {
                ActionTarget target = captureActionTargetFromAim(stand);
                keepStandAimedAtTarget(target);
                aimAs = AimingEntity.STAND;
            }
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (level.isClientSide() || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            switch (mode) {
                case QUICK_ASSEMBLY -> {
                    if (user instanceof Player player) {
                        if (stand instanceof NutKingCallEntity nkc && nkc.isDisassembleMode()) {
                            NutKingCallService.disassemble(player, level);
                        }
                        else {
                            NutKingCallService.assemble(player, level);
                        }
                    }
                }
                case TOGGLE_MODE -> {
                    if (stand instanceof NutKingCallEntity nkc) {
                        nkc.toggleDisassembleMode();
                    }
                }
                case FIRE_NAIL -> fireNails(stand);
                case DISMANTLE -> NutKingCallService.dismantle(level, user);
                case GIVE_NAIL -> giveNail(level);
                case PIN -> {
                    if (getTaskTarget(level).getEntity() instanceof LivingEntity target) {
                        NutKingCallAttachedNailEntity.pinEntity(level, target, user);
                    }
                }
                case UNPIN_TARGET -> NutKingCallService.unpinTarget(level, user);
                case PIN_SELF -> NutKingCallAttachedNailEntity.pinEntity(level, user);
                case UNPIN_SELF -> {
                    if (NailState.isPinned(user)) {
                        NutKingCallAttachedNailEntity.unpin(level, user, true);
                    }
                }
            }
            // 1.16 StandEntityTask paid the staminaCost after standPerform, whatever came of it
            StandAbilityStamina.consume(ability, StandPower.get(user), mode.staminaCost, false);
        }

        // 1.16: one to three shots, each a nail or a nut at even odds, the i-th spread by 1 + 3i
        private void fireNails(StandEntity stand) {
            Level level = level();
            int count = 1 + level.random.nextInt(3);
            for (int i = 0; i < count; i++) {
                NutKingCallProjectile projectile = level.random.nextBoolean()
                        ? new NutKingCallNutEntity(stand, level)
                        : new NutKingCallNailEntity(stand, level);
                projectile.shootFromRotation(stand, FIRE_NAIL_SPEED, stand.getProjectileInaccuracy(1.0F + i * 3.0F));
                addProjectileWithStandStats(projectile);
            }
        }

        private void giveNail(Level level) {
            ActionTarget target = getTaskTarget(level);
            if (target.getType() == ActionTarget.TargetType.ENTITY) {
                if (target.getEntity() instanceof LivingEntity living) {
                    NutKingCallAttachedNailEntity.attachNail(level, living);
                    NutKingCallAttachedNailEntity.attachNut(level, living);
                }
            }
            else if (target.getType() == ActionTarget.TargetType.BLOCK) {
                BlockPos pos = target.getBlockPos();
                Direction face = target.getFace();
                if (face != null) {
                    NutKingCallNailBlock.placeNail(level, pos, face);
                    NutKingCallNailBlock.placeNut(level, pos, face);
                }
            }
        }

        // 1.16 StandEntityAction.canClickDuringTask: no action of these was chainable
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }
}
