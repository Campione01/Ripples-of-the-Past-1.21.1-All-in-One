package rotp.addons.echoes.state;

import java.util.Comparator;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandDamageContext;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.ability.EchoesGravityAbility.Mode;
import rotp.addons.echoes.init.EchoesDataComponents;
import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.state.EchoesUserState.ActiveSkill;
import rotp.addons.echoes.state.EchoesFreezeState.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class EchoesGravityService {
    // master GRAVITY_INFUSE_RANGE and the author's 1.21.1: an item on the ground keeps its gravity this near its owner
    public static final double GRAVITY_ITEM_RANGE = 5.0D;
    // master MAX_RANGE: a freeze picks and holds within 5 blocks of its user, Stand out or not
    public static final double TARGET_FREEZE_RANGE = 5.0D;
    public static final double INFUSE_BLOCK_RANGE = 10.0D;
    // master GRAVITY_PROJECTILE_MAX_RANGE: a thrown item hits nothing further than this from its thrower
    public static final double PROJECTILE_RANGE = 30.0D;
    public static final double PROJECTILE_SPEED = 2.0D;
    public static final float PROJECTILE_DAMAGE = 6.0F;
    public static final int SELF_DRAIN_INTERVAL = 20;
    public static final float SELF_STAMINA_DRAIN = 0.05F;
    // master DisablerTagHandler and the author's 1.21.1: Slowness, Mining Fatigue and Weakness at 255 (092's Hunger is gone)
    public static final int FREEZE_EFFECT_AMPLIFIER = 255;
    // master HandFreezeHandler.freezeEntity: a hand-frozen target can still shuffle along
    public static final int HAND_FREEZE_SLOWNESS = 3;
    // master GravityItemEvents: an item in the hands is Jump -10
    private static final int HELD_ITEM_JUMP_LEVEL = 10;
    // 1.21 clamps effect levels to 0..255, so the donor's Jump 255 and
    // Freeze Self's Jump -10 would both raise the jump. The entity's jump
    // strength is held at 0 instead, as long as the freeze lasts.
    public static final ResourceLocation FREEZE_JUMP_MODIFIER =
            RotpEchoes.id("gravity_freeze_jump");
    public static final ResourceLocation SELF_FREEZE_JUMP_MODIFIER =
            RotpEchoes.id("gravity_freeze_self_jump");
    public static final ResourceLocation SHEER_OWNER_JUMP_MODIFIER =
            RotpEchoes.id("gravity_freeze_sheer_owner_jump");
    public static final ResourceLocation CARRIER_JUMP_MODIFIER =
            RotpEchoes.id("gravity_item_jump");
    public static final ResourceLocation CARRIER_FALL_MODIFIER =
            RotpEchoes.id("gravity_item_fall");
    // 1.16 LivingEntity.jumpFromGround: 0.1 per Jump level
    private static final double CARRIER_JUMP_PER_LEVEL = 0.1D;
    private static final double FROZEN_SINK_SPEED = -0.5D;
    // master GravityFreezeAction / HandFreezeAction: a frozen target is pressed down 4.5 blocks a tick
    public static final double FREEZE_FALL_SPEED = 4.5D;
    public static final ResourceLocation SELF_HAND_FREEZE_SPEED_MODIFIER =
            RotpEchoes.id("hand_freeze_self_speed");
    // 092 HandFreezeHandler: Slowness, Mining Fatigue and Weakness come off
    // once 20 of its 600 ticks are left.
    private static final long HAND_FREEZE_EASING_TICKS = 20L;

    private EchoesGravityService() {}

    public static void perform(ServerPlayer player, Mode mode) {
        switch (mode) {
            case PROJECTILE -> launchProjectile(player);
            case FREEZE_TARGET -> freezeInAim(player, Kind.TARGET);
            case HAND_FREEZE -> freezeInAim(player, Kind.HAND);
            case FREEZE_SELF -> toggleSelfFreeze(player);
            case INFUSE -> infuseInAim(player);
            case HAND_FREEZE_SELF -> toggleSelfHandFreeze(player);
            case FREEZE_CANCEL, HAND_FREEZE_CANCEL -> cancelTargetFreeze(player);
            case FREEZE_SELF_CANCEL, HAND_FREEZE_SELF_CANCEL -> cancelSelfSkill(player);
            case INFUSE_CANCEL -> cancelHeldInfusion(player);
        }
    }

    /**
     * Whether a target or hand freeze started now would catch someone: a living entity in the user's aim within
     * the freeze range. The master's and the author's freezes need one, so a miss costs nothing.
     */
    public static boolean hasFreezeTarget(LivingEntity user) {
        return aimedFreezeTarget(user) != null;
    }

    @Nullable
    private static LivingEntity aimedFreezeTarget(LivingEntity user) {
        StandPower power = StandPower.get(user);
        StandEntity performer = power != null ? power.getSummonedStandEntity() : null;
        ActionTarget aimed = HitResultUtil.clip(
                user.getEyePosition(),
                user.getLookAngle(),
                TARGET_FREEZE_RANGE,
                TARGET_FREEZE_RANGE,
                user.level(),
                entity -> entity.isAlive()
                        && !entity.isSpectator()
                        && entity != user
                        && entity != performer
                        && entity instanceof LivingEntity,
                user,
                0.0D);
        return aimed.getMainEntity() instanceof LivingEntity target ? target : null;
    }

    // master DisablerTagHandler.isFrozen and the author's: a frozen entity or a self-frozen user deals no damage and
    // takes no knockback
    public static boolean isFrozen(LivingEntity entity) {
        if (isFreezeDisabled(entity)) {
            return true;
        }
        if (entity instanceof Player player
                && player.hasData(rotp.addons.echoes.init.EchoesAttachments.USER_STATE)) {
            ActiveSkill skill = RotpEchoes.userState(player).activeSkill();
            return skill == ActiveSkill.FREEZE_SELF || skill == ActiveSkill.HAND_FREEZE_SELF;
        }
        return false;
    }

    // master GravityFreezeAction.checkSpecificConditions: frozen by another, a user cannot freeze; his own self freeze
    // only gives way to the new one
    public static boolean isFrozenByAnother(LivingEntity entity) {
        return isFreezeDisabled(entity);
    }

    // master MCUtilMixin and StandPowerLeapMixin: no leap while frozen or with a gravity item in the hands. Asked on the
    // client too (the HUD), which knows the freezes by their synced jump holds.
    public static boolean keptFromLeaping(LivingEntity user) {
        AttributeInstance jump = user.getAttribute(Attributes.JUMP_STRENGTH);
        return jump != null
                        && (jump.hasModifier(FREEZE_JUMP_MODIFIER) || jump.hasModifier(SELF_FREEZE_JUMP_MODIFIER)
                                || jump.hasModifier(SHEER_OWNER_JUMP_MODIFIER))
                || user instanceof Player player && holdsInfusedItem(player);
    }

    // the author's 1.21.1 cancels: no aim, and either key lifts the user's target or hand freeze
    private static void cancelTargetFreeze(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.FREEZE_TARGET
                || state.activeSkill() == ActiveSkill.HAND_FREEZE) {
            releaseTargetOwnedBy(player, state.frozenTarget());
            state.clearActiveSkill();
        }
    }

    // the author's 1.21.1 cancels: either key lifts either self freeze
    private static void cancelSelfSkill(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.FREEZE_SELF
                || state.activeSkill() == ActiveSkill.HAND_FREEZE_SELF) {
            state.clearActiveSkill();
            clearSelfEffects(player);
        }
    }

    // master GravityInfuseCancelAction: the infused item in the main hand, else the off hand, loses its gravity
    private static void cancelHeldInfusion(ServerPlayer player) {
        ItemStack held = isInfused(player.getMainHandItem())
                ? player.getMainHandItem()
                : player.getOffhandItem();
        clearInfusion(held);
    }

    public static boolean holdsInfusedItem(Player player) {
        return isInfused(player.getMainHandItem()) || isInfused(player.getOffhandItem());
    }

    /**
     * Whether Gravity Infuse has something to work on: an item in the hands without gravity or, with none, the block in
     * the aim or an item on the ground near (the master and the author refuse with "hold an item" otherwise).
     */
    public static boolean canInfuse(Player player) {
        return !handToInfuse(player).isEmpty()
                || aimedBlockToMark(player) != null
                || nearestItemToInfuse(player) != null;
    }

    public static boolean isInfused(ItemStack stack) {
        return !stack.isEmpty() && stack.has(EchoesDataComponents.GRAVITY_INFUSION);
    }

    public static void infuse(ItemStack stack, UUID owner) {
        if (!stack.isEmpty()) {
            stack.set(EchoesDataComponents.GRAVITY_INFUSION, new GravityInfusion(owner, false));
        }
    }

    public static void clearInfusion(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.remove(EchoesDataComponents.GRAVITY_INFUSION);
        }
    }

    public static int tickInventoryGravity(ServerPlayer player) {
        dropStaleInfusions(player);
        int count = countInfused(player);
        if (count <= 0) {
            releaseJump(player, CARRIER_JUMP_MODIFIER);
            releaseModifier(
                    player, Attributes.SAFE_FALL_DISTANCE, CARRIER_FALL_MODIFIER);
            return 0;
        }
        int amplifier = Math.min(count, 5);
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                10,
                amplifier,
                false,
                false,
                false));
        // 092 GravityItemEvents: Jump -amplifier, master's -10 with one in the hands, which 1.16 added to the
        // jump as 0.1 * (1 - level) and to the safe fall distance as
        // 1 - level (calculateFallDamage). 1.21 clamps a negative level
        // to Jump Boost I, so the two attributes carry it instead.
        int jumpLevel = holdsInfusedItem(player) ? HELD_ITEM_JUMP_LEVEL : amplifier;
        setCarrierModifier(
                player,
                Attributes.JUMP_STRENGTH,
                CARRIER_JUMP_MODIFIER,
                CARRIER_JUMP_PER_LEVEL * (1 - jumpLevel));
        setCarrierModifier(
                player,
                Attributes.SAFE_FALL_DISTANCE,
                CARRIER_FALL_MODIFIER,
                1 - jumpLevel);
        applyCarrierMotion(player, count);
        // 092 GravityFieldHandler: creative carriers have no field
        if (!player.isCreative() && !player.isSpectator()) {
            pinNearbyEntities(player);
        }
        // 092 GravityItemEvents: pressure has no creative check
        pressNearbyFrozen(player);
        return count;
    }

    // Gravity needs no timer now, so what its user can no longer hold up comes off here: the gravity of a user who has
    // left ACT3, and the old timed component of a stack saved with it.
    private static void dropStaleInfusions(ServerPlayer player) {
        boolean act3 = EchoesChatService.isAct3User(player);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            dropOldTimer(stack);
            GravityInfusion infusion = stack.get(EchoesDataComponents.GRAVITY_INFUSION);
            if (infusion != null && !act3 && infusion.owner().equals(player.getUUID())) {
                clearInfusion(stack);
            }
        }
    }

    private static void dropOldTimer(ItemStack stack) {
        if (stack.has(EchoesDataComponents.GRAVITY_INFUSED_UNTIL)) {
            stack.remove(EchoesDataComponents.GRAVITY_INFUSED_UNTIL);
        }
    }

    // 092 carrier drag ran on both sides; the client owns player motion
    public static void tickLocalCarrier(Player player) {
        int count = countInfused(player);
        if (count > 0) {
            applyCarrierMotion(player, count);
        }
    }

    /**
     * master GravityFreezeSelfAction.lockMovement and the author's DisablerTagHandler.updateFreeze: the client owns a
     * player's motion and learns of a freeze by its synced jump hold, so a frozen player stops here, falls fast out of the
     * air and sinks in water. The self hand freeze only stops his going up.
     */
    public static void tickLocalFreeze(Player player) {
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump == null || !jump.hasModifier(FREEZE_JUMP_MODIFIER) && !jump.hasModifier(SELF_FREEZE_JUMP_MODIFIER)) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean selfHand = speed != null && speed.hasModifier(SELF_HAND_FREEZE_SPEED_MODIFIER);
        double y;
        if (player.isInWater()) {
            y = FROZEN_SINK_SPEED;
        } else if (selfHand) {
            y = Math.min(0.0D, player.getDeltaMovement().y);
        } else {
            y = player.onGround() ? 0.0D : -FREEZE_FALL_SPEED;
        }
        player.setDeltaMovement(0.0D, y, 0.0D);
        player.setSwimming(false);
        player.setSprinting(false);
    }

    private static int countInfused(Player player) {
        int count = 0;
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            if (isInfused(player.getInventory().getItem(slot))) {
                count++;
            }
        }
        return count;
    }

    // 092 GravityItemEvents: drag, and a carrier sinks in water
    private static void applyCarrierMotion(Player player, int count) {
        Vec3 movement = player.getDeltaMovement();
        boolean inWater = player.isInWater();
        double[] next = EchoesGravityField.carrierMotion(
                movement.x, movement.y, movement.z, count, inWater, player.onGround());
        player.setDeltaMovement(next[0], next[1], next[2]);
        if (inWater) {
            player.setSwimming(false);
            player.xxa = 0.0F;
            player.zza = 0.0F;
        }
    }

    // 092 GravityFieldHandler: living entities near a carrier are pinned
    private static void pinNearbyEntities(ServerPlayer player) {
        for (LivingEntity entity : player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(EchoesGravityField.FIELD_RANGE),
                other -> other != player && other.isAlive())) {
            if (!EchoesGravityField.inField(entity.distanceTo(player))
                    || isFreezeDisabled(entity)) {
                continue;
            }
            Vec3 movement = entity.getDeltaMovement();
            double[] next = EchoesGravityField.pinnedMotion(
                    movement.x, movement.y, movement.z);
            entity.setDeltaMovement(next[0], next[1], next[2]);
        }
    }

    // 092 DisablerTag counterpart: Echoes-frozen entities are skipped
    private static boolean isFreezeDisabled(LivingEntity entity) {
        return entity.hasData(
                        rotp.addons.echoes.init
                                .EchoesAttachments.FREEZE_STATE)
                && RotpEchoes.freezeState(entity).active();
    }

    // 092 GravityItemEvents: with another Echoes user near the carrier,
    // the block under each frozen entity breaks every 20 ticks. 092 asked
    // no rule; only an Echoes user whose Stand may break blocks (the game
    // rule and his toggle) presses here.
    private static void pressNearbyFrozen(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!EchoesGravityField.pressureTick(level.getGameTime())
                || level.getEntitiesOfClass(
                        Player.class,
                        player.getBoundingBox().inflate(
                                EchoesGravityField.PRESSURE_RANGE),
                        other -> other != player && other.isAlive()
                                && hasEchoesStand(other)
                                && JojoModUtil.breakingBlocksEnabled(level, other)).isEmpty()) {
            return;
        }
        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(
                        EchoesGravityField.PRESSURE_RANGE),
                other -> other != player && other.isAlive()
                        && isFreezeDisabled(other))) {
            if (!EchoesGravityField.inPressure(entity.distanceTo(player))) {
                continue;
            }
            BlockPos below = entity.blockPosition().below();
            BlockState state = level.getBlockState(below);
            if (EchoesGravityField.pressureBreaks(
                    state.isAir(), state.is(Blocks.BEDROCK))) {
                level.destroyBlock(below, true);
            }
        }
    }

    // 092 hasEchoesStand: any act, summoned or not
    private static boolean hasEchoesStand(Player player) {
        StandPower power = StandPower.get(player);
        if (power == null) {
            return false;
        }
        StandType type = power.getPowerType();
        return type != null
                && (type == EchoesStands.ECHOES_ACT1.get()
                        || type == EchoesStands.ECHOES_ACT2.get()
                        || type == EchoesStands.ECHOES_ACT3.get());
    }

    // master and the author's GravityItemEvents: nobody picks it up while it has gravity, it sinks in water, and on the
    // ground it loses the gravity once its owner is too far. The author's own check left it on when its owner logged off,
    // and so out of reach for good, so here it ends then and when he has gone to another level.
    public static void tickInfusedItem(ItemEntity item) {
        ItemStack stack = item.getItem();
        dropOldTimer(stack);
        GravityInfusion infusion = stack.get(EchoesDataComponents.GRAVITY_INFUSION);
        if (infusion == null || !(item.level() instanceof ServerLevel level)) {
            return;
        }
        Player owner = level.getPlayerByUUID(infusion.owner());
        spawnInfusedItemParticles(level, item, infusion.thrown());
        if (infusion.thrown()) {
            tickThrownItem(level, item, stack, infusion, owner);
        } else if (owner == null || owner.distanceTo(item) > GRAVITY_ITEM_RANGE) {
            clearInfusion(stack);
            return;
        }
        if (item.isInWater() && level.getGameTime() % 5L == 0L) {
            Vec3 movement = item.getDeltaMovement();
            item.setDeltaMovement(movement.x, -0.3D, movement.z);
        }
        item.setPickUpDelay(40);
    }

    /**
     * The author's GravityItemEvents.onItemEntityTick: a throw is a flight, and it hits the first living thing in its
     * way. Over the way the item came rather than the one ahead of it, so that it cannot hit through a wall, and his
     * Stand and he are not in the way, whom the author's check let it hit.
     */
    private static void tickThrownItem(ServerLevel level, ItemEntity item, ItemStack stack, GravityInfusion infusion,
            @Nullable Player owner) {
        Vec3 from = new Vec3(item.xo, item.yo, item.zo);
        Vec3 to = item.position();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, item, from, to,
                new AABB(from, to).inflate(1.0D),
                entity -> entity instanceof LivingEntity
                        && entity.isAlive()
                        && !entity.isSpectator()
                        && entity != owner
                        && !(entity instanceof StandEntity stand && stand.getUser() == owner));
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            try (var running = StandDamageContext.enter(RotpEchoes.isEchoesUser(owner) ? StandPower.get(owner) : null)) {
                target.hurt(level.damageSources().thrown(item, owner), PROJECTILE_DAMAGE);
            }
            target.setDeltaMovement(target.getDeltaMovement().add(to.subtract(from).normalize().scale(0.5D)));
            level.sendParticles(ParticleTypes.CRIT, item.getX(), item.getY() + 0.2D, item.getZ(),
                    12, 0.3D, 0.3D, 0.3D, 0.1D);
            item.setDeltaMovement(Vec3.ZERO);
            item.hurtMarked = true;
        } else if (owner != null && owner.distanceTo(item) <= PROJECTILE_RANGE
                && !item.onGround() && !item.horizontalCollision && !item.verticalCollision) {
            return;
        }
        stack.set(EchoesDataComponents.GRAVITY_INFUSION, new GravityInfusion(infusion.owner(), false));
    }

    // the author's GravityItemEvents.spawnGravityParticles
    private static void spawnInfusedItemParticles(ServerLevel level, ItemEntity item, boolean thrown) {
        if (level.getGameTime() % 2L != 0L) {
            return;
        }
        if (thrown) {
            level.sendParticles(ParticleTypes.END_ROD, item.getX(), item.getY() + 0.1D, item.getZ(),
                    2, 0.05D, 0.05D, 0.05D, 0.01D);
            level.sendParticles(ParticleTypes.CRIT, item.getX(), item.getY() + 0.1D, item.getZ(),
                    1, 0.1D, 0.1D, 0.1D, 0.02D);
            return;
        }
        for (int i = 0; i < 2; i++) {
            level.sendParticles(
                    ParticleTypes.ENCHANT,
                    item.getX() + (level.getRandom().nextDouble() - 0.5D) * 0.8D,
                    item.getY() + 0.1D + level.getRandom().nextDouble() * 0.5D,
                    item.getZ() + (level.getRandom().nextDouble() - 0.5D) * 0.8D,
                    1,
                    0.0D,
                    0.02D + level.getRandom().nextDouble() * 0.03D,
                    0.0D,
                    0.01D);
        }
    }

    public static void tickFrozenTarget(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)
                || !target.hasData(
                        rotp.addons.echoes.init
                                .EchoesAttachments.FREEZE_STATE)) {
            return;
        }
        EchoesFreezeState state = RotpEchoes.freezeState(target);
        if (!state.active()) {
            return;
        }
        if (EchoesFreezeTiming.expired(state.expiresAt(), level.getGameTime())) {
            releaseFrozenTarget(target);
            return;
        }
        if (state.kind() == Kind.SHEER_HEART_OWNER) {
            tickSheerHeartOwner(level, target, state);
            return;
        }
        ServerPlayer owner = level.getServer()
                .getPlayerList()
                .getPlayer(state.owner());
        StandPower power = owner != null ? StandPower.get(owner) : null;
        // A freeze goes on through a recall (owner's rule of 2026-11-05) and ends, Stand out or not, at its own deadline,
        // its own cancel, its user's loss of the Stand, or when he has walked out of range (master
        // GravityFreezeAction.tickTarget and the author's updateFreeze measure from him).
        if (owner == null
                || owner.level() != level
                || power == null
                || !RotpEchoes.isEchoesUser(owner)
                || !inFreezeReach(owner, target)) {
            releaseFrozenTarget(target);
            return;
        }

        state.initializeLegacyDeadline(level.getGameTime());
        if (target instanceof ServerPlayer) {
            // its client moves it (tickLocalFreeze); a push from here would only fight its moves
            state.moveAnchor(target.position());
        } else {
            pressFrozen(target, state);
        }
        spawnFreezeParticles(level, target);
        target.fallDistance = 0.0F;
        if (target instanceof Mob mob && state.kind() == Kind.TARGET) {
            mob.getNavigation().stop();
        }
        restrainFrozen(target, state.kind());
        interruptHeldInputs(target);
        holdSheerHeartOwner(target, state);
        // Every tick: the transient hold is not saved, and the last 20 ticks
        // of a hand freeze skip the effect refresh below.
        holdJump(target, FREEZE_JUMP_MODIFIER);
        if (state.kind() == Kind.HAND && target instanceof Player player) {
            // 092 HandFreezeHandler.tickFreeze
            player.swing(InteractionHand.MAIN_HAND, false);
            player.swing(InteractionHand.OFF_HAND, false);
        }
        if (state.kind() == Kind.HAND
                && state.expiresAt() - level.getGameTime()
                        <= HAND_FREEZE_EASING_TICKS) {
            // 092 HandFreezeHandler.tickFreeze, in the last 20 ticks
            target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            target.removeEffect(MobEffects.DIG_SLOWDOWN);
            target.removeEffect(MobEffects.WEAKNESS);
        } else if (target.tickCount % 20 == 0) {
            applyTargetEffects(target, 40, state.kind());
        }
    }

    // master SheerHeartAttackCompat.tick: while his Sheer Heart Attack is frozen, its owner's hands are, wherever he is. Asked
    // again each tick, so that a relog or the end of another's freeze on him does not lose it.
    private static void holdSheerHeartOwner(LivingEntity car, EchoesFreezeState carState) {
        if (!EchoesSheerHeartCompat.isSheerHeart(car)) {
            return;
        }
        LivingEntity carOwner = EchoesSheerHeartCompat.ownerOf(car);
        if (carOwner == null) {
            return;
        }
        EchoesFreezeState state = RotpEchoes.freezeState(carOwner);
        if (!state.active()) {
            state.activate(carOwner, car.getUUID(), 0, Kind.SHEER_HEART_OWNER, carState.expiresAt());
            applyTargetEffects(carOwner, 40, Kind.SHEER_HEART_OWNER);
            restrainFrozen(carOwner, Kind.SHEER_HEART_OWNER);
        }
    }

    // master SheerHeartAttackCompat.applyOwnerHandFreeze and HandFreezeHandler.tickFreeze: no press and no range; it ends with
    // the car's freeze. His own jump hold keeps his client's tickLocalFreeze out: he walks and falls as he would.
    private static void tickSheerHeartOwner(ServerLevel level, LivingEntity carOwner, EchoesFreezeState state) {
        if (!(findEntity(level.getServer(), state.owner()) instanceof LivingEntity car)
                || !car.isAlive()
                || !isFreezeDisabled(car)) {
            releaseFrozenTarget(carOwner);
            return;
        }
        restrainFrozen(carOwner, state.kind());
        holdJump(carOwner, SHEER_OWNER_JUMP_MODIFIER);
        if (carOwner instanceof Player player) {
            player.swing(InteractionHand.MAIN_HAND, false);
            player.swing(InteractionHand.OFF_HAND, false);
        }
        if (carOwner.tickCount % 20 == 0) {
            applyTargetEffects(carOwner, 40, state.kind());
        }
    }

    // A mob's or a non-server player's motion is the server's: master tickTarget presses it down 4.5 blocks a tick, and
    // 092 DisablerTagHandler.updateFreeze sets it to 0, -0.5, 0 in water just before it moves, so it sinks; a target freeze
    // holds it where it ends up, and a hand-frozen target's own steps still count.
    private static void pressFrozen(LivingEntity target, EchoesFreezeState state) {
        if (target.isInWater()
                && (target instanceof Player || target instanceof Mob)) {
            target.setDeltaMovement(0.0D, FROZEN_SINK_SPEED, 0.0D);
        } else {
            if (state.kind() == Kind.TARGET) {
                target.setPos(state.anchor().x, target.getY(), state.anchor().z);
            }
            target.move(MoverType.SELF, new Vec3(0.0D, -FREEZE_FALL_SPEED, 0.0D));
            target.setDeltaMovement(Vec3.ZERO);
        }
        state.moveAnchor(target.position());
        target.setNoGravity(true);
    }

    // master GravityFreezeAction.tickActiveTargets and the author's updateFreeze, on all he freezes: glyphs sink around it
    private static void spawnFreezeParticles(ServerLevel level, LivingEntity entity) {
        if (level.getGameTime() % 5L != 0L) {
            return;
        }
        int count = 2 + level.getRandom().nextInt(3);
        for (int i = 0; i < count; i++) {
            level.sendParticles(
                    ParticleTypes.ENCHANT,
                    entity.getX() + (level.getRandom().nextDouble() - 0.5D),
                    entity.getY() + 0.5D + level.getRandom().nextDouble() * 2.0D,
                    entity.getZ() + (level.getRandom().nextDouble() - 0.5D),
                    1,
                    0.0D,
                    -(0.02D + level.getRandom().nextDouble() * 0.04D),
                    0.0D,
                    0.01D);
        }
    }

    // master EchoesGameplayEventHandler.interruptHeldActions: whoever is frozen lets go of his held moves
    private static void interruptHeldInputs(LivingEntity entity) {
        StandPower stand = StandPower.get(entity);
        if (stand != null) {
            stand.interruptHeldInputs();
        }
        PlayerPower playerPower = PlayerPower.get(entity);
        if (playerPower != null) {
            playerPower.interruptHeldInputs();
        }
    }

    public static void tickUser(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.NONE) {
            return;
        }
        StandPower power = StandPower.get(player);
        if (!RotpEchoes.isEchoesUser(player)
                || power == null) {
            cleanupUser(player, true);
            return;
        }
        if (state.activeSkill() == ActiveSkill.FREEZE_SELF
                || state.activeSkill() == ActiveSkill.HAND_FREEZE_SELF) {
            // master GravityFreezeSelfAction.tickSelfFreeze: a toggle that runs until the stamina does (the owner's choice
            // over the author's fixed 30 seconds)
            if (player.tickCount % SELF_DRAIN_INTERVAL == 0
                    && !power.isStaminaInfinite()) {
                // the upkeep is a cost, so it is paid in the Stands page's stamina share (EV-STANDCFG item4)
                float next = power.getStamina() - power.getStaminaCostShare(SELF_STAMINA_DRAIN);
                if (next <= 0.0F) {
                    state.clearActiveSkill();
                    clearSelfEffects(player);
                    return;
                }
                power.consumeStamina(SELF_STAMINA_DRAIN);
            }
            if (state.activeSkill() == ActiveSkill.FREEZE_SELF) {
                applySelfEffects(player);
                player.fallDistance = 0.0F;
            } else {
                applySelfHandEffects(player);
            }
            Abilities abilities = player.getAbilities();
            if (abilities.flying) {
                abilities.flying = false;
                player.onUpdateAbilities();
            }
            spawnFreezeParticles(player.serverLevel(), player);
            interruptHeldInputs(player);
        } else if ((state.activeSkill() == ActiveSkill.FREEZE_TARGET
                        || state.activeSkill() == ActiveSkill.HAND_FREEZE)
                && findEntity(
                                player.getServer(),
                                state.frozenTarget())
                        == null) {
            state.clearActiveSkill();
        }
    }

    public static void cleanupUser(
            ServerPlayer player, boolean removeInfusedItems) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.FREEZE_TARGET
                || state.activeSkill() == ActiveSkill.HAND_FREEZE) {
            releaseTargetOwnedBy(player, state.frozenTarget());
        }
        if (state.activeSkill() == ActiveSkill.FREEZE_SELF
                || state.activeSkill() == ActiveSkill.HAND_FREEZE_SELF) {
            clearSelfEffects(player);
        }
        if (state.activeSkill() == ActiveSkill.GRAVITY_INFUSE) {
            removeOwnedMarks(player.getServer(), player.getUUID());
        }
        state.clearActiveSkill();
        if (removeInfusedItems) {
            clearInfusedInventory(player);
        }
    }

    public static void releaseFrozenTarget(LivingEntity target) {
        EchoesFreezeState freezeState = RotpEchoes.freezeState(target);
        if (!freezeState.active()) {
            return;
        }
        UUID ownerId = freezeState.owner();
        freezeState.clear(target);
        clearTargetEffects(target);
        if (ownerId == null
                || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = level.getServer()
                .getPlayerList()
                .getPlayer(ownerId);
        if (owner != null) {
            EchoesUserState userState = RotpEchoes.userState(owner);
            if (target.getUUID().equals(userState.frozenTarget())) {
                userState.clearActiveSkill();
            }
        }
    }

    public static boolean isHandFrozen(LivingEntity target) {
        // master HandFreezeSelfAction freezes the user's own hands
        if (target instanceof Player player
                && player.hasData(rotp.addons.echoes.init.EchoesAttachments.USER_STATE)
                && RotpEchoes.userState(player).activeSkill() == ActiveSkill.HAND_FREEZE_SELF) {
            return true;
        }
        return target.hasData(
                        rotp.addons.echoes.init
                                .EchoesAttachments.FREEZE_STATE)
                && RotpEchoes.freezeState(target).active()
                && RotpEchoes.freezeState(target).kind().handsOnly();
    }

    public static void removeOwnedMarks(
            MinecraftServer server, UUID owner) {
        for (ServerLevel level : server.getAllLevels()) {
            GravityMarkSavedData.get(level).removeOwner(owner);
        }
    }

    // master GravityProjectileAction and the author's: from the hands. The flight is tickThrownItem's.
    private static void launchProjectile(ServerPlayer player) {
        ItemStack source = takeHeldInfusedItem(player);
        if (source.isEmpty()) {
            return;
        }
        StandPower power = StandPower.get(player);
        // 092 GravityProjectileAction.standPerform paid its cost itself once
        // it found an item, and StandEntityTask paid it again after the
        // perform: 200 in all, both results ignored.
        if (power != null) {
            power.consumeStamina(Mode.PROJECTILE.staminaCost());
        }
        source.set(EchoesDataComponents.GRAVITY_INFUSION, new GravityInfusion(player.getUUID(), true));
        ItemEntity projectile = new ItemEntity(
                player.level(),
                player.getX(),
                player.getEyeY() - 0.1D,
                player.getZ(),
                source);
        projectile.setDeltaMovement(
                player.getLookAngle().scale(PROJECTILE_SPEED));
        projectile.setPickUpDelay(20);
        projectile.setThrower(player);
        player.level().addFreshEntity(projectile);
    }

    // The author's and the master's freezes take whoever is in the aim, so a press with a freeze on moves it there;
    // the cancel keys lift it, and the toggle of 092 would stand in the way of moving it.
    private static void freezeInAim(ServerPlayer player, Kind kind) {
        LivingEntity target = aimedFreezeTarget(player);
        if (target == null
                || !inFreezeReach(player, target)) {
            return;
        }
        // 092: skill switch only after the target and range checks
        clearConflictingSkill(player);
        startFreeze(player, target, kind);
        EchoesChatService.playSkillSound(player);
    }

    private static void toggleSelfFreeze(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.FREEZE_SELF) {
            state.clearActiveSkill();
            clearSelfEffects(player);
            return;
        }
        clearConflictingSkill(player);
        state.activateSelfFreeze();
        applySelfEffects(player);
        EchoesChatService.playSkillSound(player);
    }

    // master HandFreezeSelfAction: the user's own hands and feet are frozen, toggled, draining stamina like Self Freeze
    private static void toggleSelfHandFreeze(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.HAND_FREEZE_SELF) {
            state.clearActiveSkill();
            clearSelfEffects(player);
            return;
        }
        clearConflictingSkill(player);
        state.activateSelfHandFreeze();
        applySelfHandEffects(player);
        EchoesChatService.playSkillSound(player);
    }

    // The author's GravityInfuseAction.handleItemInfuse and the master's: what is in the hands comes first. 092's mark
    // on the aimed block, which neither of them can reach any more, stays for empty hands; looking at the floor no
    // longer takes the press from the item held.
    private static void infuseInAim(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() != ActiveSkill.GRAVITY_INFUSE) {
            clearConflictingSkill(player);
            state.activateGravityInfuse();
        }
        BlockPos position = handToInfuse(player).isEmpty() ? aimedBlockToMark(player) : null;
        if (position != null) {
            toggleBlockInfusion(player, position);
            return;
        }
        infuseItem(player);
    }

    private static ItemStack handToInfuse(Player player) {
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && !isInfused(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return !off.isEmpty() && !isInfused(off) ? off : ItemStack.EMPTY;
    }

    @Nullable
    private static BlockPos aimedBlockToMark(Player player) {
        ActionTarget aimed = HitResultUtil.clip(
                player.getEyePosition(),
                player.getLookAngle(),
                INFUSE_BLOCK_RANGE,
                INFUSE_BLOCK_RANGE,
                player.level(),
                entity -> false,
                player,
                0.0D,
                // 092 GravityInfuseAction.getTargetBlock: an OUTLINE ray, so grass, a flower or a torch is the block
                ClipContext.Block.OUTLINE);
        BlockPos position = aimed.getBlockPos();
        return position != null
                        && player.level().getBlockState(position)
                                .getBlock()
                                .asItem()
                                != net.minecraft.world.item.Items.AIR
                ? position
                : null;
    }

    @Nullable
    private static ItemEntity nearestItemToInfuse(Player player) {
        return player.level()
                .getEntitiesOfClass(
                        ItemEntity.class,
                        player.getBoundingBox().inflate(GRAVITY_ITEM_RANGE),
                        item -> item.isAlive() && !item.getItem().isEmpty() && !isInfused(item.getItem()))
                .stream()
                .min(Comparator.comparingDouble(
                        player::distanceToSqr))
                .orElse(null);
    }

    private static void toggleBlockInfusion(
            ServerPlayer player, BlockPos position) {
        GravityMarkSavedData data =
                GravityMarkSavedData.get(player.serverLevel());
        GravityMarkSavedData.Mark existing =
                data.mark(position, player.level().getGameTime());
        if (existing != null
                && existing.owner().equals(player.getUUID())) {
            data.remove(position);
            RotpEchoes.userState(player).clearActiveSkill();
            return;
        }
        removeOwnedMarks(player.getServer(), player.getUUID());
        data.mark(
                position,
                player.getUUID(),
                player.level().getGameTime()
                        + GravityMarkSavedData.MARK_LIFETIME_TICKS);
        EchoesChatService.playSkillSound(player);
    }

    // One at a time (the master's revertOwnGravityItems, the author's onServerTick): the gravity moves to the new item.
    private static void infuseItem(ServerPlayer player) {
        ItemStack hand = handToInfuse(player);
        ItemEntity nearest = hand.isEmpty() ? nearestItemToInfuse(player) : null;
        RotpEchoes.userState(player).clearActiveSkill();
        if (hand.isEmpty() && nearest == null) {
            return;
        }
        revertOwnGravityItems(player);
        removeOwnedMarks(player.getServer(), player.getUUID());
        if (!hand.isEmpty() && hand.getCount() == 1) {
            // stays in the hand: the throw and the cancel ask for it there
            infuse(hand, player.getUUID());
        } else {
            ItemStack source = hand.isEmpty() ? nearest.getItem() : hand;
            ItemStack single = source.split(1);
            infuse(single, player.getUUID());
            if (nearest != null && source.isEmpty()) {
                nearest.discard();
            }
            if (!player.getInventory().add(single)) {
                player.drop(single, false);
            }
        }
        EchoesChatService.playSkillSound(player);
    }

    private static void revertOwnGravityItems(ServerPlayer player) {
        clearInfusedInventory(player);
        for (ItemEntity item : player.level().getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(GRAVITY_ITEM_RANGE),
                item -> isInfused(item.getItem()))) {
            GravityInfusion infusion = item.getItem().get(EchoesDataComponents.GRAVITY_INFUSION);
            if (infusion != null && infusion.owner().equals(player.getUUID())) {
                clearInfusion(item.getItem());
            }
        }
    }

    private static void startFreeze(ServerPlayer player, LivingEntity target, Kind kind) {
        EchoesFreezeState targetState = RotpEchoes.freezeState(target);
        if (targetState.active()) {
            releaseFrozenTarget(target);
        }
        int amplifier = distanceAmplifier(player.distanceTo(target));
        targetState.activate(
                target,
                player.getUUID(),
                amplifier,
                kind,
                EchoesFreezeTiming.deadlineAfter(player.level().getGameTime()));
        if (kind == Kind.HAND) {
            RotpEchoes.userState(player).activateHandFreeze(target.getUUID());
        } else {
            RotpEchoes.userState(player).activateTargetFreeze(target.getUUID());
        }
        applyTargetEffects(
                target, (int) EchoesFreezeTiming.DURATION_TICKS, kind);
        restrainFrozen(target, kind);
    }

    /**
     * R772 F11, master FinisherAction and GravityFreezeAction.startFreeze: the target the ACT3 finisher hit is frozen
     * as the freeze freezes, without the freeze's sounds, when it is within the freeze's reach; the user's
     * own target or hand freeze ends first.
     */
    public static void freezeFinisherTarget(ServerPlayer player, LivingEntity target) {
        if (!target.isAlive() || !inFreezeReach(player, target)) {
            return;
        }
        clearConflictingSkill(player);
        startFreeze(player, target, Kind.TARGET);
    }

    // 092 FinisherAction: an ACT3 finisher hit ends self-freeze or infuse
    public static void onAct3FinisherHit(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        ActiveSkill ended = state.activeSkill();
        if (ended == ActiveSkill.FREEZE_SELF || ended == ActiveSkill.HAND_FREEZE_SELF) {
            clearSelfEffects(player); // slowness, held jump, weakness
        } else if (ended == ActiveSkill.GRAVITY_INFUSE) {
            clearInfusedInventory(player); // un-infuse held items
            removeOwnedMarks(player.getServer(), player.getUUID()); // block marks
        } else {
            // target and hand freezes stay on, as in 092
            return;
        }
        state.clearActiveSkill();
    }

    private static void clearConflictingSkill(ServerPlayer player) {
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.activeSkill() == ActiveSkill.FREEZE_TARGET
                || state.activeSkill() == ActiveSkill.HAND_FREEZE) {
            releaseTargetOwnedBy(player, state.frozenTarget());
        } else if (state.activeSkill() == ActiveSkill.FREEZE_SELF
                || state.activeSkill() == ActiveSkill.HAND_FREEZE_SELF) {
            clearSelfEffects(player);
        } else if (state.activeSkill() == ActiveSkill.GRAVITY_INFUSE) {
            clearInfusedInventory(player);
            removeOwnedMarks(player.getServer(), player.getUUID());
        }
        state.clearActiveSkill();
    }

    private static void releaseTargetOwnedBy(
            ServerPlayer player, @Nullable UUID targetId) {
        Entity target = findEntity(player.getServer(), targetId);
        if (target instanceof LivingEntity living
                && RotpEchoes.freezeState(living).active()
                && player.getUUID().equals(
                        RotpEchoes.freezeState(living).owner())) {
            releaseFrozenTarget(living);
        }
    }

    // From the user's eyes to the target's box, as the aim that picks it reaches: the feet-to-feet distance of the master
    // and the author would let go at once of a target picked at the edge of the range.
    private static boolean inFreezeReach(LivingEntity user, LivingEntity target) {
        return target.getBoundingBox().distanceToSqr(user.getEyePosition())
                <= TARGET_FREEZE_RANGE * TARGET_FREEZE_RANGE;
    }

    @Nullable
    private static Entity findEntity(
            MinecraftServer server, @Nullable UUID entityId) {
        if (entityId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    // the author's handleFireProjectile threw from the main hand only, though its check also passed for the off hand
    private static ItemStack takeHeldInfusedItem(ServerPlayer player) {
        ItemStack held = isInfused(player.getMainHandItem())
                ? player.getMainHandItem()
                : player.getOffhandItem();
        return isInfused(held) ? held.split(1) : ItemStack.EMPTY;
    }

    private static void clearInfusedInventory(ServerPlayer player) {
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            clearInfusion(player.getInventory().getItem(slot));
        }
    }

    // 255 stops walking (Slowness), mining (Mining Fatigue) and melee damage
    // (Weakness); there is no Resistance, so the frozen target can still be
    // hurt. A hand-frozen target can still shuffle along.
    private static void applyTargetEffects(
            LivingEntity target, int duration, Kind kind) {
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                duration,
                kind.handsOnly() ? HAND_FREEZE_SLOWNESS : FREEZE_EFFECT_AMPLIFIER,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN,
                duration,
                FREEZE_EFFECT_AMPLIFIER,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                duration,
                FREEZE_EFFECT_AMPLIFIER,
                false,
                false,
                false));
        holdJump(target, kind == Kind.SHEER_HEART_OWNER ? SHEER_OWNER_JUMP_MODIFIER : FREEZE_JUMP_MODIFIER);
    }

    // 092 DisablerTagHandler (both freezes) and HandFreezeHandler (the hand
    // freeze), at the start and on every tick: a frozen player has no
    // movement input, cannot fly, stops using its item and restarts its
    // attack charge; a hand-frozen mob loses its target. Release gives the
    // player back the flight it had (EchoesFreezeState.clear). 092 cancels
    // the use (stopUsingItem), so a drawn bow or a charged trident is not
    // let go.
    private static void restrainFrozen(LivingEntity target, Kind kind) {
        if (target instanceof Player player) {
            player.xxa = 0.0F;
            player.zza = 0.0F;
            player.setSpeed(0.0F);
            player.setSwimming(false);
            Abilities abilities = player.getAbilities();
            if (abilities.flying || abilities.mayfly) {
                abilities.flying = false;
                abilities.mayfly = false;
                player.onUpdateAbilities();
            }
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            player.resetAttackStrengthTicker();
        }
        if (kind.handsOnly() && target instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    private static void applySelfEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                40,
                10,
                false,
                false,
                false));
        // 092 GravityFreezeSelfAction: Jump -10, which kept 1.16 grounded
        holdJump(player, SELF_FREEZE_JUMP_MODIFIER);
        // 092 GravityFreezeSelfAction: Weakness III (field_76437_t)
        player.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                40,
                2,
                false,
                false,
                false));
        // master and the author's GravityFreezeSelfAction
        player.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN, 40, FREEZE_EFFECT_AMPLIFIER, false, false, false));
    }

    // master HandFreezeHandler.freezeEntity on the user and HandFreezeSelfAction.lockMovement: Slowness IV, Mining
    // Fatigue and Weakness 255, no jump, and no walking (a synced speed hold, as the player moves itself)
    private static void applySelfHandEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false, false));
        player.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN, 40, FREEZE_EFFECT_AMPLIFIER, false, false, false));
        player.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, 40, FREEZE_EFFECT_AMPLIFIER, false, false, false));
        holdJump(player, SELF_FREEZE_JUMP_MODIFIER);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(SELF_HAND_FREEZE_SPEED_MODIFIER)) {
            speed.addTransientModifier(new AttributeModifier(
                    SELF_HAND_FREEZE_SPEED_MODIFIER,
                    -1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        player.setSprinting(false);
        player.setSwimming(false);
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
    }

    private static void clearTargetEffects(LivingEntity target) {
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        target.removeEffect(MobEffects.DIG_SLOWDOWN);
        target.removeEffect(MobEffects.WEAKNESS);
        releaseJump(target, FREEZE_JUMP_MODIFIER);
        releaseJump(target, SHEER_OWNER_JUMP_MODIFIER);
    }

    private static void clearSelfEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.DIG_SLOWDOWN);
        releaseJump(player, SELF_FREEZE_JUMP_MODIFIER);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(SELF_HAND_FREEZE_SPEED_MODIFIER)) {
            // only the self hand freeze adds the speed hold
            speed.removeModifier(SELF_HAND_FREEZE_SPEED_MODIFIER);
        }
    }

    private static void holdJump(
            LivingEntity entity, ResourceLocation modifier) {
        AttributeInstance jump =
                entity.getAttribute(Attributes.JUMP_STRENGTH);
        AttributeModifier current =
                jump != null ? jump.getModifier(modifier) : null;
        // An unchanged hold is not replaced, so it is not sent again.
        if (jump != null && (current == null
                || current.amount() != -1.0D
                || current.operation()
                        != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)) {
            jump.addOrUpdateTransientModifier(new AttributeModifier(
                    modifier,
                    -1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void releaseJump(
            LivingEntity entity, ResourceLocation modifier) {
        releaseModifier(entity, Attributes.JUMP_STRENGTH, modifier);
    }

    private static void releaseModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            ResourceLocation modifier) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifier);
        }
    }

    // Both attributes are synced: a modifier added again marks them for a
    // packet even when its amount is the same, so it is only replaced when
    // the amount changes.
    private static void setCarrierModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            ResourceLocation modifier,
            double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(modifier);
        if (current == null || current.amount() != amount) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(
                    modifier,
                    amount,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static int distanceAmplifier(double distance) {
        return distance <= 3.0D ? 10 : distance <= 6.0D ? 8 : 6;
    }
}
