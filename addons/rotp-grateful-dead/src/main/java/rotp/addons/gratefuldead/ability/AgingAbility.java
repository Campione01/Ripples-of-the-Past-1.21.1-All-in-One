package rotp.addons.gratefuldead.ability;

import java.util.Optional;
import java.util.UUID;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.StandPrecisionRay;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.effect.AgingAuraEffect;
import rotp.addons.gratefuldead.service.AgingService;
import rotp.addons.gratefuldead.state.BlockAgingData;
import rotp.addons.gratefuldead.state.LivingAgingState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AgingAbility extends EntityActionAbility {
    public enum Mode {
        TOUCH,
        AURA,
        REMOVE,
        // Next Album 0.1.13 the_grateful_dead_aging_self,
        // _remove_self_aging and _remove_all_aging
        SELF,
        REMOVE_SELF,
        REMOVE_ALL
    }

    /** 0.1.13 Remove Aging reach: 4096 squared, 64 blocks. */
    public static final double REMOVE_RANGE = 64.0D;
    public static final int REMOVE_BLOCK_FADE_TICKS = 60;

    private final Mode mode;

    public AgingAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Mode mode) {
        super(
                abilityType,
                abilityId,
                type -> new AgingAction(type, mode));
        this.mode = mode;
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
        if (mode == Mode.TOUCH || mode == Mode.SELF) {
            setButtonHoldPhase(ActionPhase.PERFORM);
        }
        else {
            cooldown(mode == Mode.AURA ? 10 : 40);
        }
    }

    public boolean isHeldMode() {
        return mode == Mode.TOUCH || mode == Mode.SELF;
    }

    public boolean isTouchMode() {
        return mode == Mode.TOUCH;
    }

    // 1.16 the_grateful_dead_aging_touch, _aging_aura and _remove_aging
    // (025 InitStands :109-111) were StandActions its user's power
    // performed, the Stand out or not
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 StandAction.getPerformer :191-193: the summoned Stand, else the
    // user, whose stun PowerBaseImpl.checkRequirements :264 checked at the
    // press and on every held tick
    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        return stand != null ? stand : user;
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        if (level.isClientSide()) {
            return null;
        }
        return setOrBufferAction(level, user, getPerformer(user),
                inputMethod, extraClientInput, clickHoldResolveTime,
                bufferingState);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return null;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action, Level level,
            LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (isHeldMode()) {
            StandPower power = powerUser != null ? StandPower.get(powerUser) : null;
            double durability = power != null && power.getPowerType() != null
                    ? power.getPowerType().getStandStats().durability(power.getStatsDevelopment())
                    : 8.0D;
            action.phasesLength.put(ActionPhase.PERFORM,
                    (float) Math.max(40, (int) (60.0D + durability * 6.0D)));
        }
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck parent =
                super.checkSpecificConditions(context);
        if (!parent.isPositive()) {
            return parent;
        }
        if (!(context instanceof StandPower power
                && power.getPowerType()
                        == GratefulDeadAddon
                                .THE_GRATEFUL_DEAD.get())) {
            return ConditionCheck.NEGATIVE;
        }
        if (mode == Mode.SELF) {
            return checkSelf(context.getUser());
        }
        return mode == Mode.TOUCH
                ? checkTouch(context.getUser())
                : ConditionCheck.POSITIVE;
    }

    private static ConditionCheck checkSelf(LivingEntity user) {
        return user == null || AgingService.isAgeableLiving(user)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(TouchRefusal.NOT_LIVING);
    }

    // 1.16 re-ran these checks on every held tick.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action,
            Power<?> context) {
        ConditionCheck parent =
                super.checkHeldSpecificConditions(
                        action, context);
        if (parent.isPositive() && mode == Mode.SELF) {
            return checkSelf(context.getUser());
        }
        if (!parent.isPositive() || mode != Mode.TOUCH) {
            return parent;
        }
        return checkTouch(context.getUser());
    }

    // 1.16 TheGratefulDeadAgingTouchAction refusals, same target as performTouch.
    private static ConditionCheck checkTouch(
            LivingEntity user) {
        if (user == null) {
            return ConditionCheck.POSITIVE;
        }
        Level level = user.level();
        ActionTarget target = touchTarget(user, level);
        Entity entity = target.getMainEntity();
        BlockPos targetPos = entity != null
                ? entity.blockPosition()
                : target.getBlockPos();
        boolean hit = targetPos != null;
        boolean ageable;
        if (entity instanceof ItemEntity item) {
            ageable = AgingService.isAgeableItem(
                    item.getItem());
        }
        else if (entity instanceof LivingEntity living) {
            ageable = AgingService.isAgeableLiving(
                    AgingService.canonicalLivingTarget(
                            living));
        }
        else {
            ageable = entity == null && hit
                    && AgingService.isAgeableBlock(
                            level.getBlockState(targetPos));
        }
        String refusal = TouchRefusal.of(
                hasAgeableHeldItem(user),
                hit,
                ageable);
        return refusal == null
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(refusal);
    }

    // 1.16 took the touch's target from its client's crosshair
    // (InputHandler.pickMouseTarget): the vanilla pick, and behind a miss
    // with the Stand out the Stand's precision ray
    // (EntityStandType.clientHitResult). Action.checkRangeAndTarget
    // :162-201 measured it from the performer, the summoned Stand, else
    // the user (an entity 8 blocks, 4 unseen, a block 10), and
    // PowerBaseImpl.checkTarget emptied a target out of range
    // (TargetRequirement.NONE).
    private static ActionTarget touchTarget(
            LivingEntity user, Level level) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        ActionTarget target = crosshairTarget(user, level);
        if (target.getType() == ActionTarget.TargetType.EMPTY
                && stand != null) {
            target = StandEntityPunchAbility.aimAssist(user)
                    ? StandPrecisionRay.trace(user, stand)
                    : StandPrecisionRay.crosshair(user, stand,
                            StandPrecisionRay.reach(user, stand), true);
            target = target.resolveEntityId(level);
        }
        return ActionTargetRange.isTargetWithinRange(
                stand != null ? stand : user, target, level)
                ? target
                : ActionTarget.EMPTY;
    }

    // The aims only go from the user's client to the server, so that
    // client reads the one it sends.
    private static ActionTarget crosshairTarget(
            LivingEntity user, Level level) {
        ActionTargetAim aim = level.isClientSide()
                && user == ClientProxy.getClientPlayer()
                ? ClientsideAim.playerAim
                : LivingComponentAction.getAim(user);
        ActionTarget target = aim != null ? aim.getTarget() : null;
        return target != null
                ? target.resolveEntityId(level)
                : ActionTarget.EMPTY;
    }

    private static boolean hasAgeableHeldItem(
            LivingEntity user) {
        for (InteractionHand hand :
                InteractionHand.values()) {
            if (AgingService.isAgeableItem(
                    user.getItemInHand(hand))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 1.16 checkTarget then checkConditions, as they ran together. 0.1.13
     * dropped the cold refusals: the touch works everywhere and the aging
     * rate only drives the aura and the recovery.
     */
    public static final class TouchRefusal {
        public static final String COLD =
                "aging_touch_cold_biome";
        public static final String NOT_LIVING =
                "aging_touch_not_living";

        private TouchRefusal() {}

        /** The refusal key, or null when the touch may go on. */
        public static String of(
                boolean heldAgeableItem,
                boolean targetHit,
                boolean targetAgeable) {
            if (heldAgeableItem || targetHit && targetAgeable) {
                return null;
            }
            return NOT_LIVING;
        }
    }

    @Override
    public Component getName(Power<?> context) {
        if (mode != Mode.AURA
                || !(context instanceof StandPower power)) {
            return super.getName(context);
        }
        return Component.translatable(
                getTranslationKey(
                        auraActive(power)
                                ? ".deactivate"
                                : ".activate"));
    }

    // 0.1.13 TheGratefulDeadAgingAuraAction.getIconTexturePath: the "off" picture while no aura runs
    @Override
    public String getSpriteName(Power<?> context) {
        if (mode == Mode.AURA
                && context instanceof StandPower power
                && !auraActive(power)) {
            return AURA_OFF_SPRITE;
        }
        return super.getSpriteName(context);
    }

    public static final String AURA_OFF_SPRITE =
            GratefulDeadAddon.AGING_AURA + "_off";

    private static boolean auraActive(StandPower power) {
        return power.userStandEffects
                .getEffectOfType(
                        GratefulDeadAddon
                                .AGING_AURA_EFFECT.get())
                .isPresent();
    }

    public static final class AgingAction
            extends EntityActionInstance {
        private final Mode mode;

        public AgingAction(
                EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void onButtonStopHold() {
            if (mode == Mode.TOUCH || mode == Mode.SELF) {
                startRecovery();
            }
        }

        @Override
        public void actionPerformStart() {
            if (mode == Mode.AURA || mode == Mode.REMOVE_ALL) {
                performToggle();
            }
            else if (mode == Mode.REMOVE) {
                performRemoveLooked();
            }
            else if (mode == Mode.REMOVE_SELF) {
                performRemoveSelf();
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM) {
                return;
            }
            if (mode == Mode.TOUCH) {
                performTouch();
            }
            else if (mode == Mode.SELF) {
                performSelf();
            }
        }

        // 0.1.13 TheGratefulDeadAgingSelfAction.onHoldTick: a player ages
        // himself at his own aging speed (a self-aging takes no Stand penalty).
        private void performSelf() {
            if (!(level() instanceof ServerLevel level)
                    || !(getPowerUser() instanceof Player user)
                    || !AgingService.isAgeableLiving(user)) {
                return;
            }
            float speed = AgingService.livingAgingMultiplier(user);
            if (speed <= 0.0F) {
                return;
            }
            UUID owner = user.getUUID();
            AgingService.ageLiving(
                    user,
                    owner,
                    AgingService.generation(level.getServer(), owner),
                    AgingService.SELF_LIVING_DELTA * speed,
                    true);
        }

        private void performRemoveSelf() {
            LivingEntity user = getPowerUser();
            if (user != null && level() instanceof ServerLevel
                    && user.hasData(GratefulDeadAddon.LIVING_AGING)) {
                LivingAgingState.get(user).clearImmediately(user);
            }
        }

        // 0.1.13 TheGratefulDeadRemoveAgingAction.perform: the looked-at
        // entity or block within 64 blocks, else the aging of the held items.
        private void performRemoveLooked() {
            if (!(level() instanceof ServerLevel level)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            Vec3 eye = user.getEyePosition();
            Vec3 end = eye.add(user.getViewVector(1.0F).scale(REMOVE_RANGE));
            BlockHitResult block = level.clip(new ClipContext(
                    eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, user));
            Vec3 reach = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            EntityHitResult entity = ProjectileUtil.getEntityHitResult(
                    user, eye, reach,
                    user.getBoundingBox().expandTowards(reach.subtract(eye)).inflate(1.0D),
                    candidate -> !candidate.isSpectator() && candidate.isPickable()
                            || candidate instanceof ItemEntity,
                    eye.distanceToSqr(reach));
            if (entity != null) {
                clearEntity(user, entity.getEntity());
            }
            else if (block.getType() == HitResult.Type.BLOCK) {
                BlockAgingData.get(level).startFade(
                        level, block.getBlockPos(), REMOVE_BLOCK_FADE_TICKS);
            }
            else {
                for (InteractionHand hand : InteractionHand.values()) {
                    user.getItemInHand(hand).remove(GratefulDeadAddon.ITEM_AGING);
                }
            }
        }

        private static void clearEntity(LivingEntity user, Entity entity) {
            if (entity instanceof ItemEntity item) {
                ItemStack stack = item.getItem().copy();
                if (stack.has(GratefulDeadAddon.ITEM_AGING)) {
                    stack.remove(GratefulDeadAddon.ITEM_AGING);
                    item.setItem(stack);
                }
                return;
            }
            if (!(entity instanceof LivingEntity living)) {
                return;
            }
            LivingEntity target = AgingService.canonicalLivingTarget(living);
            if (target == user) {
                return;
            }
            if (target.hasData(GratefulDeadAddon.LIVING_AGING)) {
                LivingAgingState.get(target).clearImmediately(target);
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                target.getItemBySlot(slot).remove(GratefulDeadAddon.ITEM_AGING);
            }
            if (target instanceof Player player) {
                for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
                    player.getInventory().getItem(index).remove(GratefulDeadAddon.ITEM_AGING);
                }
                player.containerMenu.getCarried().remove(GratefulDeadAddon.ITEM_AGING);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            }
        }

        private void performToggle() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power = user != null
                    ? StandPower.get(user)
                    : null;
            if (user == null || power == null) {
                return;
            }
            Optional<AgingAuraEffect> active =
                    power.userStandEffects
                            .getEffectOfType(
                                    GratefulDeadAddon
                                            .AGING_AURA_EFFECT
                                            .get());
            if (mode == Mode.AURA) {
                if (active.isPresent()) {
                    power.userStandEffects.removeEffect(
                            active.get());
                    AgingService.requestFade(
                            level().getServer(),
                            user.getUUID());
                }
                else {
                    power.userStandEffects.addEffect(
                            GratefulDeadAddon
                                    .AGING_AURA_EFFECT
                                    .get()
                                    .create(level())
                                    .withStand(power));
                }
            }
            else if (mode == Mode.REMOVE_ALL) {
                active.ifPresent(
                        power.userStandEffects::removeEffect);
                AgingService.requestFade(
                        level().getServer(),
                        user.getUUID());
            }
        }

        private void performTouch() {
            if (!(level()
                    instanceof ServerLevel level)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            UUID owner = user.getUUID();
            long generation = AgingService.generation(
                    level.getServer(), owner);
            // 0.1.13: the touch ages at a fixed rate, whatever the climate.
            float targetSpeed = 1.0F;
            if (ageHeldItem(
                    user,
                    owner,
                    generation,
                    targetSpeed)) {
                return;
            }

            ActionTarget target = touchTarget(user, level);
            Entity entity = target.getMainEntity();
            if (entity instanceof LivingEntity living) {
                AgingService.ageLiving(
                        living,
                        owner,
                        generation,
                        AgingService.TOUCH_LIVING_DELTA
                                * targetSpeed,
                        true);
            }
            else if (entity instanceof ItemEntity item) {
                ItemStack aged = AgingService.ageItem(
                        item.getItem(),
                        owner,
                        generation,
                        AgingService.TOUCH_ITEM_DELTA
                                * targetSpeed);
                if (aged.isEmpty()) {
                    item.discard();
                }
                else {
                    item.setItem(aged);
                }
            }
            else if (target.getBlockPos() != null) {
                BlockAgingData.get(level).age(
                        level,
                        target.getBlockPos(),
                        owner,
                        generation,
                        AgingService.TOUCH_BLOCK_DELTA
                                * targetSpeed);
            }
        }

        private static boolean ageHeldItem(
                LivingEntity user,
                UUID owner,
                long generation,
                float speedMultiplier) {
            for (InteractionHand hand :
                    InteractionHand.values()) {
                ItemStack stack =
                        user.getItemInHand(hand);
                if (!AgingService.isAgeableItem(stack)) {
                    continue;
                }
                if (speedMultiplier > 0.0F) {
                    user.setItemInHand(
                            hand,
                            AgingService.ageItem(
                                    stack,
                                    owner,
                                    generation,
                                    AgingService
                                            .TOUCH_ITEM_DELTA
                                            * speedMultiplier));
                }
                return true;
            }
            return false;
        }
    }
}
