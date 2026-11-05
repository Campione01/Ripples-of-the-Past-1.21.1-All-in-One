package rotp.addons.madeinheaven.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import rotp.core.api.control.LivingSwingDurationQuery;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.LightSpeedDashAbility;
import rotp.addons.madeinheaven.ability.TimeSystemAbility;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * How living beings take the acceleration. Mode A (others fall behind): seen
 * from the owner, every other living being in the Overworld runs m times
 * slower. A being the server moves updates only on every m-th tick, its
 * position and rotation interpolated in between; a player (and the mount a
 * player steers) is moved by its own client, so it runs on scaled
 * attributes, and the core's rates slow his Stand actions, cooldowns and the
 * motion no attribute covers. Mode B (the world accelerates): living beings
 * keep their own time and the owner adapts to the faster world.
 */
public final class TimeLagService {
    public enum Mode {
        NONE,
        TICK,
        ATTRIBUTE
    }

    private static final ResourceLocation MOVEMENT_MODIFIER =
            MadeInHeavenAddon.id("time_lag_movement");
    private static final ResourceLocation SWIM_MODIFIER =
            MadeInHeavenAddon.id("time_lag_swim");
    private static final ResourceLocation ATTACK_SPEED_MODIFIER =
            MadeInHeavenAddon.id("time_lag_attack_speed");
    private static final ResourceLocation BLOCK_BREAK_MODIFIER =
            MadeInHeavenAddon.id("time_lag_block_break");
    private static final ResourceLocation JUMP_MODIFIER =
            MadeInHeavenAddon.id("time_lag_jump");
    private static final ResourceLocation GRAVITY_MODIFIER =
            MadeInHeavenAddon.id("time_lag_gravity");
    // Mode B: the owner keeps up with the accelerated world.
    private static final ResourceLocation OWNER_ATTACK_SPEED_MODIFIER =
            MadeInHeavenAddon.id("time_adaptation_attack_speed");
    private static final ResourceLocation OWNER_BLOCK_BREAK_MODIFIER =
            MadeInHeavenAddon.id("time_adaptation_block_break");
    private static final ResourceLocation OWNER_MOVEMENT_MODIFIER =
            MadeInHeavenAddon.id("time_adaptation_movement");
    private static final ResourceLocation OWNER_SWIM_MODIFIER =
            MadeInHeavenAddon.id("time_adaptation_swim");
    private static final ResourceLocation OWNER_STEP_MODIFIER =
            MadeInHeavenAddon.id("time_adaptation_step");
    // Abilities.flyingSpeed is saved with the player: the unscaled value is
    // kept beside it so a crash or an autosave cannot leave a slow flyer.
    static final String BASE_FLYING_SPEED_KEY =
            MadeInHeavenAddon.MOD_ID + ":time_lag_base_flying_speed";

    private static final Map<LivingEntity, Integer> ATTRIBUTE_LAGGED =
            new WeakHashMap<>();
    private static final Map<LivingEntity, TickTrack> TICK_LAGGED =
            new WeakHashMap<>();
    private static final Map<LivingEntity, Integer> OWNER_ADAPTED =
            new WeakHashMap<>();

    private TimeLagService() {}

    public static int multiplier(Level level) {
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        return state.active()
                ? TimeLagRates.lagMultiplier(state.phase())
                : 1;
    }

    /**
     * How this living being falls behind, on either side: its lag, unless
     * another source holds it back more (owner rule G4: the slowest rate
     * wins, as with Neo Stand's bullet time), which then acts alone.
     */
    public static Mode mode(LivingEntity entity) {
        Mode mode = lagMode(entity);
        if (mode != Mode.NONE
                && !EntityTimeRates.governs(EntityTimeRates.Channel.BEING,
                        entity, TimeAccelerationRates.OWNER)) {
            return Mode.NONE;
        }
        return mode;
    }

    /** The lag the acceleration itself gives this being (mode A only). */
    static Mode lagMode(LivingEntity entity) {
        Level level = entity.level();
        if (level.dimension() != Level.OVERWORLD) {
            return Mode.NONE;
        }
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        UUID owner = state.ownerUuid();
        if (!state.othersFallBehind() || owner == null) {
            return Mode.NONE;
        }
        return mode(entity, owner, 0);
    }

    /** The owner while he accelerates time, in either mode. */
    public static boolean isAcceleratingOwner(LivingEntity entity) {
        Level level = entity.level();
        return level.dimension() == Level.OVERWORLD
                && entity.isAlive()
                && MadeInHeavenAddon.accelerationState(level)
                        .isOwner(entity.getUUID());
    }

    /** Mode B: the owner, who adapts to the world he accelerates. */
    public static boolean isAdaptedOwner(LivingEntity entity) {
        return isAcceleratingOwner(entity)
                && MadeInHeavenAddon.accelerationState(entity.level())
                        .worldAccelerates();
    }

    // A Stand's time is its user's.
    private static LivingEntity timeSubject(LivingEntity entity) {
        return entity instanceof StandEntity stand
                && stand.getUser() != null
                ? stand.getUser()
                : entity;
    }

    /**
     * EntityTimeRates BEING (A2-COMBINE): 1/m for every being held back
     * (mode A), m for the owner in either mode (his time keeps up with the
     * accelerated world: other sources such as bullet time can exempt him
     * by it), no answer for anyone else.
     */
    public static float beingRate(LivingEntity entity) {
        LivingEntity subject = timeSubject(entity);
        if (lagMode(subject) != Mode.NONE) {
            return (float) TimeLagRates.rateFactor(multiplier(subject.level()));
        }
        return isAcceleratingOwner(subject)
                ? multiplier(subject.level())
                : EntityTimeRates.NO_RATE;
    }

    /**
     * EntityTimeRates STAND_ACTIONS, asked for a user on both sides: a
     * slowed player's Stand actions and cooldowns run m times slower (a
     * held-back mob's skipped ticks already hold them back); in mode B the
     * owner's run m times faster, except the dash and the Time System's
     * hold, whose lengths are 1.16 holds in the world's time. Time Aura
     * strikes at the faster pace but counts its upkeep and its hold in
     * server ticks (TimeAuraAbility.TimeAuraAction.countPerformTick).
     */
    public static float standActionRate(LivingEntity user) {
        if (mode(user) == Mode.ATTRIBUTE) {
            return (float) TimeLagRates.rateFactor(multiplier(user.level()));
        }
        return isAdaptedOwner(user) && !keepsWorldTime(user)
                ? multiplier(user.level())
                : EntityTimeRates.NO_RATE;
    }

    private static boolean keepsWorldTime(LivingEntity owner) {
        Object ability = currentStandAbility(owner);
        return ability instanceof LightSpeedDashAbility
                || ability instanceof TimeSystemAbility;
    }

    private static Object currentStandAbility(LivingEntity user) {
        StandPower power = StandPower.get(user);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        EntityActionInstance action = stand != null
                ? stand.getCurStandAction()
                : null;
        return action != null && !action.isOver() ? action.ability : null;
    }

    /**
     * EntityTimeRates MOTION, both sides: a slowed player's ladders,
     * levitation, bubble columns, elytra boost and Jump Boost run m times
     * slower. The owner walks at the world's pace in mode B.
     */
    public static float motionRate(LivingEntity entity) {
        return mode(entity) == Mode.ATTRIBUTE
                ? (float) TimeLagRates.rateFactor(multiplier(entity.level()))
                : EntityTimeRates.NO_RATE;
    }

    /**
     * The client clock of a living being (core ClientEntityClockRates): a
     * held-back being's idle animations slow down with it, the adapted
     * owner's speed up; a Stand keeps its user's clock.
     */
    public static float clockRate(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return EntityTimeRates.NO_RATE;
        }
        LivingEntity subject = timeSubject(living);
        if (mode(subject) != Mode.NONE) {
            return (float) TimeLagRates.rateFactor(multiplier(subject.level()));
        }
        return isAdaptedOwner(subject)
                ? multiplier(subject.level())
                : EntityTimeRates.NO_RATE;
    }

    private static Mode mode(
            LivingEntity entity, UUID owner, int depth) {
        if (depth > 4
                || !entity.isAlive()
                || entity instanceof ArmorStand
                || owner.equals(entity.getUUID())
                || carries(entity, owner)) {
            return Mode.NONE;
        }
        // A Stand keeps to its user, as the core's time stop freezes it
        // exactly when it freezes the user: the Stand of a slowed player
        // follows that player and is not held back on its own.
        if (entity instanceof StandEntity stand) {
            LivingEntity user = stand.getUser();
            if (user != null) {
                return mode(user, owner, depth + 1) == Mode.TICK
                        ? Mode.TICK
                        : Mode.NONE;
            }
        }
        // A time stopper is the only one moving in his own time stop.
        if (movesInStoppedTime(entity)) {
            return Mode.NONE;
        }
        if (entity instanceof Player player) {
            return TimeStopState.gameModeIgnoresTimeStop(player)
                    ? Mode.NONE
                    : Mode.ATTRIBUTE;
        }
        if (entity.getControllingPassenger() instanceof Player rider) {
            return mode(rider, owner, depth + 1) == Mode.ATTRIBUTE
                    ? Mode.ATTRIBUTE
                    : Mode.NONE;
        }
        return Mode.TICK;
    }

    private static boolean carries(LivingEntity entity, UUID owner) {
        if (!entity.isVehicle()) {
            return false;
        }
        for (Entity passenger : entity.getIndirectPassengers()) {
            if (owner.equals(passenger.getUUID())) {
                return true;
            }
        }
        return false;
    }

    private static boolean movesInStoppedTime(LivingEntity entity) {
        Level level = entity.level();
        if (level.isClientSide()) {
            return TimeStopState.getClientDisplayInstance(
                            new ChunkPos(entity.blockPosition()))
                    .isPresent()
                    && !TimeStopState.shouldFreezeClientEntity(entity);
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP)) {
            return false;
        }
        TimeStopState timeStop =
                serverLevel.getData(ModDataAttachmentTypes.TIME_STOP);
        return timeStop.isTimeStopped(entity)
                && !timeStop.shouldFreeze(entity);
    }

    // Server: the core's time stop (normal priority) cancels a frozen
    // entity's tick first, and a cancelled tick never reaches this.
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living.level().isClientSide()) {
            return;
        }
        TickTrack track = TICK_LAGGED.get(living);
        if (mode(living) != Mode.TICK) {
            if (track != null) {
                TICK_LAGGED.remove(living);
                track.finish(living);
            }
            return;
        }
        if (track == null) {
            track = new TickTrack();
            TICK_LAGGED.put(living, track);
        }
        // tickNonPassenger counted a cancelled tick before the event; a mob
        // only reselects goals when (tickCount + id) is even, so at an even
        // multiplier one id parity would never act.
        if (track.skip(living, multiplier(living.level()))) {
            event.setCanceled(true);
            living.tickCount--;
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living.level().isClientSide()) {
            return;
        }
        TickTrack track = TICK_LAGGED.get(living);
        if (track != null) {
            track.afterRealTick(living);
        }
    }

    public static void onEntityLeaveLevel(Entity entity) {
        if (entity instanceof LivingEntity living
                && !entity.level().isClientSide()) {
            TICK_LAGGED.remove(living);
        }
    }

    // Server, every tick of the acceleration: players and the mounts they
    // steer, in the Overworld or not, get exactly the modifiers they need.
    public static void reconcileAttributes(
            Iterable<? extends ServerPlayer> players) {
        Set<LivingEntity> lagged =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Set<LivingEntity> adapted =
                Collections.newSetFromMap(new IdentityHashMap<>());
        for (ServerPlayer player : players) {
            reconcile(player, lagged);
            if (player.getVehicle() instanceof LivingEntity mount) {
                reconcile(mount, lagged);
            }
            reconcileOwner(player, adapted);
        }
        Iterator<Map.Entry<LivingEntity, Integer>> iterator =
                ATTRIBUTE_LAGGED.entrySet().iterator();
        while (iterator.hasNext()) {
            LivingEntity entity = iterator.next().getKey();
            if (!lagged.contains(entity)) {
                iterator.remove();
                removeAttributeLag(entity);
            }
        }
        Iterator<Map.Entry<LivingEntity, Integer>> owners =
                OWNER_ADAPTED.entrySet().iterator();
        while (owners.hasNext()) {
            LivingEntity entity = owners.next().getKey();
            if (!adapted.contains(entity)) {
                owners.remove();
                removeOwnerAdaptation(entity);
            }
        }
    }

    // Mode B, as 1.16 boostOnAcceleration: the owner attacks, mines and
    // (onUseItemTick) uses items at the world's pace, walks at normal speed
    // and takes 1.16's phase-based speed boost only while he sprints or
    // swims, from phase 4 with the 1.16 higher step.
    private static void reconcileOwner(
            ServerPlayer player, Set<LivingEntity> adapted) {
        if (!isAdaptedOwner(player)) {
            return;
        }
        int multiplier = multiplier(player.level());
        // the dash's own 1.16 Speed does not stack on the boost
        boolean boosted = (player.isSprinting()
                || player.isSwimming() && player.isInWater())
                && !(currentStandAbility(player)
                        instanceof LightSpeedDashAbility);
        int phase = Math.max(0, TimeAccelerationService.phase(player.level()));
        int sprint = boosted ? phase : -1;
        boolean step = boosted
                && phase >= TimeAccelerationService.BUFF_PHASE;
        int key = multiplier * 1024 + (sprint + 1) * 2 + (step ? 1 : 0);
        adapted.add(player);
        Integer applied = OWNER_ADAPTED.get(player);
        if (applied == null || applied != key) {
            applyOwnerAdaptation(player, multiplier, sprint, step);
            OWNER_ADAPTED.put(player, key);
        }
    }

    static void applyOwnerAdaptation(
            LivingEntity owner, int multiplier, int sprint, boolean step) {
        updateModifier(owner, Attributes.ATTACK_SPEED,
                OWNER_ATTACK_SPEED_MODIFIER, multiplier - 1.0D);
        updateModifier(owner, Attributes.BLOCK_BREAK_SPEED,
                OWNER_BLOCK_BREAK_MODIFIER, multiplier - 1.0D);
        // sprint is the phase while the boost runs, -1 otherwise
        if (sprint > 0) {
            double boost = TimeLagRates.ownerSprintFactor(sprint) - 1.0D;
            updateModifier(owner, Attributes.MOVEMENT_SPEED,
                    OWNER_MOVEMENT_MODIFIER, boost);
            updateModifier(owner, NeoForgeMod.SWIM_SPEED,
                    OWNER_SWIM_MODIFIER, boost);
        }
        else {
            removeModifier(owner, Attributes.MOVEMENT_SPEED,
                    OWNER_MOVEMENT_MODIFIER);
            removeModifier(owner, NeoForgeMod.SWIM_SPEED,
                    OWNER_SWIM_MODIFIER);
        }
        AttributeInstance stepHeight =
                owner.getAttribute(Attributes.STEP_HEIGHT);
        if (step && stepHeight != null) {
            stepHeight.addOrUpdateTransientModifier(new AttributeModifier(
                    OWNER_STEP_MODIFIER,
                    Math.max(0.0D, TimeLagRates.ownerStepHeight(sprint)
                            - stepHeight.getBaseValue()),
                    AttributeModifier.Operation.ADD_VALUE));
        }
        else {
            removeModifier(owner, Attributes.STEP_HEIGHT,
                    OWNER_STEP_MODIFIER);
        }
    }

    static void removeOwnerAdaptation(LivingEntity owner) {
        removeModifier(owner, Attributes.ATTACK_SPEED,
                OWNER_ATTACK_SPEED_MODIFIER);
        removeModifier(owner, Attributes.BLOCK_BREAK_SPEED,
                OWNER_BLOCK_BREAK_MODIFIER);
        removeModifier(owner, Attributes.MOVEMENT_SPEED,
                OWNER_MOVEMENT_MODIFIER);
        removeModifier(owner, NeoForgeMod.SWIM_SPEED, OWNER_SWIM_MODIFIER);
        removeModifier(owner, Attributes.STEP_HEIGHT, OWNER_STEP_MODIFIER);
    }

    private static void reconcile(
            LivingEntity entity, Set<LivingEntity> lagged) {
        if (mode(entity) != Mode.ATTRIBUTE) {
            return;
        }
        int multiplier = multiplier(entity.level());
        lagged.add(entity);
        Integer applied = ATTRIBUTE_LAGGED.get(entity);
        if (applied == null || applied != multiplier) {
            applyAttributeLag(entity, multiplier);
            ATTRIBUTE_LAGGED.put(entity, multiplier);
        }
    }

    /** Every path that ends the acceleration ends its lag here. */
    public static void endAll(MinecraftServer server) {
        List<Map.Entry<LivingEntity, TickTrack>> tracks =
                new ArrayList<>(TICK_LAGGED.entrySet());
        TICK_LAGGED.clear();
        for (Map.Entry<LivingEntity, TickTrack> entry : tracks) {
            entry.getValue().finish(entry.getKey());
        }
        List<LivingEntity> lagged =
                new ArrayList<>(ATTRIBUTE_LAGGED.keySet());
        ATTRIBUTE_LAGGED.clear();
        for (LivingEntity entity : lagged) {
            removeAttributeLag(entity);
        }
        List<LivingEntity> adapted =
                new ArrayList<>(OWNER_ADAPTED.keySet());
        OWNER_ADAPTED.clear();
        for (LivingEntity entity : adapted) {
            removeOwnerAdaptation(entity);
        }
        for (ServerPlayer player :
                server.getPlayerList().getPlayers()) {
            removeAttributeLag(player);
            removeOwnerAdaptation(player);
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        ATTRIBUTE_LAGGED.remove(player);
        removeAttributeLag(player);
        OWNER_ADAPTED.remove(player);
        removeOwnerAdaptation(player);
    }

    // A base flying speed saved beside the scaled one outlived its
    // acceleration (a crash, or an autosave before the server died).
    public static void onPlayerLogin(ServerPlayer player) {
        restoreFlyingSpeed(player);
    }

    static void applyAttributeLag(LivingEntity entity, int multiplier) {
        double rate = TimeLagRates.rateFactor(multiplier);
        updateModifier(entity, Attributes.MOVEMENT_SPEED,
                MOVEMENT_MODIFIER, rate - 1.0D);
        updateModifier(entity, NeoForgeMod.SWIM_SPEED,
                SWIM_MODIFIER, rate - 1.0D);
        updateModifier(entity, Attributes.ATTACK_SPEED,
                ATTACK_SPEED_MODIFIER, rate - 1.0D);
        updateModifier(entity, Attributes.BLOCK_BREAK_SPEED,
                BLOCK_BREAK_MODIFIER, rate - 1.0D);
        updateModifier(entity, Attributes.JUMP_STRENGTH,
                JUMP_MODIFIER,
                TimeLagRates.jumpStrengthFactor(multiplier) - 1.0D);
        updateModifier(entity, Attributes.GRAVITY,
                GRAVITY_MODIFIER,
                TimeLagRates.gravityFactor(multiplier) - 1.0D);
        if (entity instanceof ServerPlayer player) {
            if (!player.getAbilities().mayfly) {
                restoreFlyingSpeed(player);
                return;
            }
            CompoundTag data = player.getPersistentData();
            float base = data.contains(BASE_FLYING_SPEED_KEY)
                    ? data.getFloat(BASE_FLYING_SPEED_KEY)
                    : player.getAbilities().getFlyingSpeed();
            data.putFloat(BASE_FLYING_SPEED_KEY, base);
            player.getAbilities().setFlyingSpeed((float) (base * rate));
            player.onUpdateAbilities();
        }
    }

    static void removeAttributeLag(LivingEntity entity) {
        removeModifier(entity, Attributes.MOVEMENT_SPEED,
                MOVEMENT_MODIFIER);
        removeModifier(entity, NeoForgeMod.SWIM_SPEED, SWIM_MODIFIER);
        removeModifier(entity, Attributes.ATTACK_SPEED,
                ATTACK_SPEED_MODIFIER);
        removeModifier(entity, Attributes.BLOCK_BREAK_SPEED,
                BLOCK_BREAK_MODIFIER);
        removeModifier(entity, Attributes.JUMP_STRENGTH, JUMP_MODIFIER);
        removeModifier(entity, Attributes.GRAVITY, GRAVITY_MODIFIER);
        // Not the instance a respawn left behind: its connection already
        // belongs to the new player, who starts from his own abilities.
        if (entity instanceof ServerPlayer player && !player.isRemoved()) {
            restoreFlyingSpeed(player);
        }
    }

    private static void restoreFlyingSpeed(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.contains(BASE_FLYING_SPEED_KEY)) {
            player.getAbilities().setFlyingSpeed(
                    data.getFloat(BASE_FLYING_SPEED_KEY));
            data.remove(BASE_FLYING_SPEED_KEY);
            player.onUpdateAbilities();
        }
    }

    private static void updateModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            ResourceLocation id,
            double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(
                    id,
                    amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            ResourceLocation id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    // Both sides: an item in use advances on every m-th tick only. A being
    // the server holds back already skips its server ticks, so only its
    // client copy, which ticks every tick, is slowed here. Mode B: the
    // owner's advances m ticks a tick.
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        LivingEntity entity = event.getEntity();
        Mode mode = mode(entity);
        if (mode == Mode.ATTRIBUTE
                || mode == Mode.TICK && entity.level().isClientSide()) {
            int multiplier = multiplier(entity.level());
            if (multiplier > 1
                    && entity.level().getGameTime() % multiplier != 0) {
                event.setDuration(event.getDuration() + 1);
            }
        }
        else if (isAdaptedOwner(entity)) {
            int multiplier = multiplier(entity.level());
            int duration = event.getDuration();
            int next = duration - (multiplier - 1);
            // the tick's own step then ends a use that runs out here
            event.setDuration(duration > 0 ? Math.max(1, next) : next);
        }
    }

    // Client: a swing of a lagging being plays m times slower, the adapted
    // owner's m times faster. The server copy of a held-back being already
    // swings on its own ticks.
    public static int swingDuration(LivingSwingDurationQuery query) {
        LivingEntity entity = query.entity();
        if (!entity.level().isClientSide()) {
            return query.currentDuration();
        }
        if (mode(entity) != Mode.NONE) {
            return query.currentDuration() * multiplier(entity.level());
        }
        return isAdaptedOwner(entity)
                ? Math.max(1, query.currentDuration()
                        / multiplier(entity.level()))
                : query.currentDuration();
    }

    // Server: under gravity / m^2 an unscaled push would fling a slowed
    // player m^2 times as high; scaled as a jump it keeps its height.
    public static void onKnockBack(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide()
                && mode(entity) == Mode.ATTRIBUTE) {
            event.setStrength((float) (event.getStrength()
                    * TimeLagRates.jumpStrengthFactor(
                            multiplier(entity.level()))));
        }
    }

    public static void onExplosionKnockback(ExplosionKnockbackEvent event) {
        if (event.getAffectedEntity() instanceof LivingEntity entity
                && !entity.level().isClientSide()
                && mode(entity) == Mode.ATTRIBUTE) {
            event.setKnockbackVelocity(event.getKnockbackVelocity().scale(
                    TimeLagRates.jumpStrengthFactor(
                            multiplier(entity.level()))));
        }
    }

    static boolean isTickLagged(LivingEntity entity) {
        return TICK_LAGGED.containsKey(entity);
    }

    static boolean isAttributeLagged(LivingEntity entity) {
        return ATTRIBUTE_LAGGED.containsKey(entity);
    }

    static boolean isOwnerAdapted(LivingEntity entity) {
        return OWNER_ADAPTED.containsKey(entity);
    }

    /**
     * One held-back being: on the first tick of each cycle it runs a real
     * tick from its logical position, then the rest of the cycle carries
     * it along that step a 1/m at a time, so the clients see it move
     * smoothly at 1/m of its speed and its hitbox stays where it is seen.
     */
    private static final class TickTrack {
        private int multiplier = 1;
        // 0: the next tick is a real one; k: k/m of the step is done
        private int step;
        private boolean awaitingPost;
        private boolean interpolating;
        private Vec3 start = Vec3.ZERO;
        private Vec3 target = Vec3.ZERO;
        private Vec3 expected = Vec3.ZERO;
        private final float[] startRotation = new float[3];
        private final float[] targetRotation = new float[3];
        private final float[] expectedRotation = new float[3];

        // Whether this tick is skipped.
        boolean skip(LivingEntity entity, int nextMultiplier) {
            absorbOutsideChanges(entity);
            if (step == 0) {
                multiplier = Math.max(1, nextMultiplier);
                start = entity.position();
                readRotation(entity, startRotation);
                awaitingPost = true;
                interpolating = false;
                return false;
            }
            step++;
            apply(entity, (double) step / multiplier);
            if (step >= multiplier) {
                step = 0;
            }
            // Its hurt cooldown still runs in real time: a being that
            // falls behind is no harder to hit again.
            if (entity.invulnerableTime > 0) {
                entity.invulnerableTime--;
            }
            return true;
        }

        void afterRealTick(LivingEntity entity) {
            if (!awaitingPost) {
                return;
            }
            awaitingPost = false;
            target = entity.position();
            readRotation(entity, targetRotation);
            if (multiplier <= 1) {
                step = 0;
                return;
            }
            step = 1;
            apply(entity, 1.0D / multiplier);
        }

        // The rest of the step at once: the being stands where its last
        // real tick left it.
        void finish(LivingEntity entity) {
            if (interpolating && step != 0) {
                absorbOutsideChanges(entity);
                apply(entity, 1.0D);
            }
            step = 0;
            interpolating = false;
        }

        // A teleport, a piston, a grab moved it between real ticks: the
        // step moves with it.
        private void absorbOutsideChanges(LivingEntity entity) {
            if (!interpolating) {
                return;
            }
            Vec3 moved = entity.position().subtract(expected);
            if (moved.lengthSqr() > 1.0E-12D) {
                start = start.add(moved);
                target = target.add(moved);
            }
            float[] now = new float[3];
            readRotation(entity, now);
            for (int index = 0; index < 3; index++) {
                float turned = index == 1
                        ? now[index] - expectedRotation[index]
                        : Mth.wrapDegrees(
                                now[index] - expectedRotation[index]);
                if (turned != 0.0F) {
                    startRotation[index] += turned;
                    targetRotation[index] += turned;
                }
            }
        }

        private void apply(LivingEntity entity, double fraction) {
            expected = new Vec3(
                    Mth.lerp(fraction, start.x, target.x),
                    Mth.lerp(fraction, start.y, target.y),
                    Mth.lerp(fraction, start.z, target.z));
            entity.setPos(expected);
            float part = (float) fraction;
            expectedRotation[0] = Mth.rotLerp(
                    part, startRotation[0], targetRotation[0]);
            expectedRotation[1] = Mth.lerp(
                    part, startRotation[1], targetRotation[1]);
            expectedRotation[2] = Mth.rotLerp(
                    part, startRotation[2], targetRotation[2]);
            entity.setYRot(expectedRotation[0]);
            entity.setXRot(expectedRotation[1]);
            entity.setYHeadRot(expectedRotation[2]);
            interpolating = true;
        }

        private static void readRotation(
                LivingEntity entity, float[] into) {
            into[0] = entity.getYRot();
            into[1] = entity.getXRot();
            into[2] = entity.getYHeadRot();
        }
    }
}
