package rotp.core.subsystems.timestop;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import rotp.core.JojoModConfig;
import rotp.core.api.timestop.TimeStopAudioContext;
import rotp.core.api.timestop.TimeStopAudioCue;
import rotp.core.api.timestop.TimeStopAudioDecision;
import rotp.core.api.timestop.TimeStopBehaviorPolicies;
import rotp.core.api.timestop.TimeStopEntityMovementAuthorizers;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.api.timestop.TimeStopAwarenessProviders;
import rotp.core.api.timestop.TimeStopLifecycleEvent.RemovalReason;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import rotp.core.event.ModEventHooks;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStands;
import rotp.core.mixin.timestop.ServerLevelBlockEventsInvoker;
import rotp.core.modcompat.ModInteractionUtil;
import rotp.core.network.s2c.TrDirectEntityDataPacket;
import rotp.core.network.s2c.TrTimeStopInstancePacket;
import rotp.core.network.s2c.TrTimeStopPlayerStatePacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;
import rotp.core.subsystems.soul.SoulEntity;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.functions.MathUtil;
import rotp.core.util.functions.UtilFunctions;
import rotp.core.util.functions_network.NetworkUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class TimeStopState {
    // Read from PlaySoundEvent too, which can come off the client thread.
    private static final Map<Integer, TimeStopState.Instance> CLIENT_INSTANCES = new ConcurrentHashMap<>();
    public static final double TIME_STOP_SPRINT_FLOAT_START_THRESHOLD = 0.0D;
    public static final double TIME_STOP_SPRINT_FLOAT_LIFT_VELOCITY = 0.2D;
    public static final double TIME_STOP_SPRINT_FLOAT_STEADY_VELOCITY = 0.1D;
    public static final double TIME_STOP_SPRINT_FLOAT_LIFT_MULTIPLIER = 1.5D;
    private static boolean gamerulesFrozen;
    private static boolean previousDaylightCycle;
    private static boolean previousWeatherCycle;

    private final ServerLevel level;
    private final Map<Integer, TimeStopState.Instance> instances = new HashMap<>();
    // Server-only: the ability that started each instance, like 1.16 TimeStopInstance.action.
    private final Map<Integer, AbilityId> timeStopStarters = new HashMap<>();
    // Server-only: the stamina each instance's start actually took, and the part of it no early end gives back.
    private final Map<Integer, StartCharge> timeStopStartCharges = new HashMap<>();
    // Server-only: the game time each instance began stopping time. A held stop's ticksPassed starts below 0 (the
    // opening settle, already frozen), so it cannot give the start that rule G1 compares with a rewind's point.
    private final Map<Integer, Long> timeStopStartTimes = new HashMap<>();
    private final Map<Integer, FrozenEntityState> frozenEntities = new HashMap<>();
    private final Map<Integer, List<Runnable>> onTimeResume = new HashMap<>();
    private final Set<UUID> playersVisionFrozen = new HashSet<>();
    private final IntOpenHashSet dirtyEntityData = new IntOpenHashSet();
    private final Map<UUID, Map<Integer, Map<Integer, SynchedEntityData.DataValue<?>>>> delayedEntityData = new HashMap<>();
    private final TimeStopRedstoneHold redstoneHold;
    private final TimeStopScheduledTickHold scheduledTickHold;

    public TimeStopState(ServerLevel level) {
        this.level = level;
        this.redstoneHold = new TimeStopRedstoneHold(level);
        this.scheduledTickHold = new TimeStopScheduledTickHold(level);
    }

    public Collection<Instance> getInstances() {
        return instances.values();
    }

    public Optional<Instance> getInstance(int id) {
        return Optional.ofNullable(instances.get(id));
    }

    public Optional<Instance> getLongestInstanceIn(ChunkPos chunkPos) {
        return instances.values().stream()
                .filter(Instance::isActive)
                .filter(instance -> instance.covers(chunkPos))
                .max(Comparator.comparingInt(Instance::ticksLeft));
    }

    public boolean isTimeStopped(ChunkPos chunkPos) {
        return instances.values().stream().anyMatch(instance -> instance.isActive() && instance.covers(chunkPos));
    }

    public boolean isTimeStopped(Entity entity) {
        return isTimeStopped(new ChunkPos(entity.blockPosition()));
    }

    public int getTimeStopTicks(ChunkPos chunkPos) {
        return instances.values().stream()
                .filter(Instance::isActive)
                .filter(instance -> instance.covers(chunkPos))
                .mapToInt(Instance::ticksLeft)
                .max()
                .orElse(0);
    }

    public void putInstance(Instance instance) {
        tryPutInstance(instance);
    }

    public boolean tryPutInstance(Instance instance) {
        TimeStopLifecycleEvent.PreStart event =
                ModEventHooks.onTimeStopPreStart(level, instance);
        return commitPreStart(event);
    }

    @ApiStatus.Internal
    public boolean commitPreStart(TimeStopLifecycleEvent.PreStart event) {
        return commitPreStart(event, null);
    }

    /**
     * Commits the instance and remembers the ability that started it, so the instance
     * ends, and its cooldown lands, on that ability whatever its moveset name.
     */
    @ApiStatus.Internal
    public boolean commitPreStart(TimeStopLifecycleEvent.PreStart event, @Nullable Ability startingAbility) {
        return commitPreStart(event, startingAbility, 0.0F);
    }

    /**
     * Also records the stamina the start actually took. An early end refunds a share of
     * that record, and nothing when no charge was recorded.
     */
    @ApiStatus.Internal
    public boolean commitPreStart(TimeStopLifecycleEvent.PreStart event, @Nullable Ability startingAbility,
            float chargedStartCost) {
        return commitPreStart(event, startingAbility, chargedStartCost, 0.0F);
    }

    /**
     * The owner's rule for an early end (2026-10-14): the unused share of what the start took comes back,
     * except unrefundableStartCost, the price of the shortest stop (a double press's) under the same
     * policy and multiplier, which is never given back.
     */
    @ApiStatus.Internal
    public boolean commitPreStart(TimeStopLifecycleEvent.PreStart event, @Nullable Ability startingAbility,
            float chargedStartCost, float unrefundableStartCost) {
        if (event == null || event.isCanceled() || event.getLevel() != level) {
            return false;
        }
        Instance instance = event.getInstance();
        if (startingAbility != null) {
            timeStopStarters.put(instance.id(), startingAbility.abilityId);
        }
        else {
            timeStopStarters.remove(instance.id());
        }
        if (chargedStartCost > 0.0F && Float.isFinite(chargedStartCost)) {
            timeStopStartCharges.put(instance.id(), new StartCharge(chargedStartCost,
                    Float.isFinite(unrefundableStartCost) ? Math.max(unrefundableStartCost, 0.0F) : chargedStartCost));
        }
        else {
            timeStopStartCharges.remove(instance.id());
        }
        commitInstance(instance);
        return true;
    }

    private void commitInstance(Instance instance) {
        boolean firstInstance = instances.isEmpty();
        if (!firstInstance) {
            removeSoundsIfCrosses(instance);
        }
        if (firstInstance) {
            freezeTimeStopGamerulesIfNeeded();
        }
        Instance replaced = instances.put(instance.id(), instance);
        // a running stop put again (extended or cut by an add-on) keeps its first start
        if (replaced == null || !replaced.isActive() || !timeStopStartTimes.containsKey(instance.id())) {
            timeStopStartTimes.put(instance.id(), level.getGameTime());
        }
        if (replaced != null) {
            reconcileFrozenEntities();
        }
        reconcileEntitiesForInstance(instance);
        if (replaced != null) {
            ModEventHooks.onTimeStopRemoved(
                    level, replaced, RemovalReason.REPLACED);
        }
        if (instances.get(instance.id()) == instance) {
            syncAddedInstanceToAll(instance);
            ModEventHooks.onTimeStopAdded(level, instance);
        }
    }

    private void removeSoundsIfCrosses(Instance newInstance) {
        for (var entry : instances.entrySet()) {
            Instance existing = entry.getValue();
            if (existing.ticksLeft() < newInstance.ticksLeft()
                    && newInstance.covers(existing.centerPos())) {
                entry.setValue(existing.withResumeSoundAndVoiceLineUserIds(-1, -1));
            }
        }
    }

    public Awareness canPlayerSeeAndMove(ServerPlayer player) {
        if (!isTimeStopped(player)) {
            return Awareness.FREE;
        }
        boolean canMove = canPlayerMoveInStoppedTime(player);
        boolean canSee = canPlayerSeeInStoppedTime(player, canMove);
        return new Awareness(canSee, canMove);
    }

    public void sendPlayerAwareness(ServerPlayer player) {
        Awareness awareness = canPlayerSeeAndMove(player);
        PacketDistributor.sendToPlayer(player, new TrTimeStopPlayerStatePacket(awareness.canSee(), awareness.canMove()));
        updateFrozenVisionState(player, awareness.canSee());
    }

    public void sendPlayerAwarenessRestore(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new TrTimeStopPlayerStatePacket(true, true));
        unfreezePlayerVision(player);
    }

    public void resendCurrentStateToPlayer(ServerPlayer player) {
        clearClientInstancesForPlayer(player);
        resendActiveInstancesToPlayer(player);
        if (isTimeStopped(player)) {
            sendPlayerAwareness(player);
        }
        else {
            sendPlayerAwarenessRestore(player);
        }
    }

    public void resendActiveInstancesToPlayer(ServerPlayer player) {
        ChunkPos playerChunk = new ChunkPos(player.blockPosition());
        for (Instance instance : instances.values()) {
            if (instance.isActive() && instance.covers(playerChunk)) {
                syncInstanceToPlayer(player, instance);
            }
        }
    }

    public void clearActiveInstancesFromPlayer(ServerPlayer player) {
        clearClientInstancesForPlayer(player);
        PacketDistributor.sendToPlayer(player, new TrTimeStopPlayerStatePacket(true, true));
        clearFrozenVisionForPlayer(player);
    }

    private void updateAllPlayerAwareness() {
        for (ServerPlayer player : level.players()) {
            sendPlayerAwareness(player);
        }
    }

    private ServerPlayer getServerPlayerByEntityId(int entityId) {
        for (ServerPlayer player : level.players()) {
            if (player.getId() == entityId) {
                return player;
            }
        }
        return null;
    }

    @Nullable
    private LivingEntity getLivingEntityById(int entityId) {
        Entity entity = level.getEntity(entityId);
        if (entity == null && entityId >= 0) {
            // 1.16 TimeStopInstance held its user: a player keeps the same entity (and id) in another
            // dimension, so the stop goes on and settles on them. Other entities get a new id there.
            for (ServerLevel otherLevel : level.getServer().getAllLevels()) {
                if (otherLevel != level && otherLevel.getEntity(entityId) instanceof Player player) {
                    return player;
                }
            }
        }
        return entity instanceof LivingEntity living ? living : null;
    }

    public boolean shouldFreeze(Entity entity) {
        if (!isFreezable(entity)) {
            return false;
        }
        LivingEntity boundOwner = ownerBoundTo(entity);
        if (boundOwner != null) {
            return shouldFreeze(boundOwner);
        }
        return isTimeStopped(entity) && !canEntityMoveInStoppedTime(entity);
    }

    // 1.16 OwnerBoundProjectileEntity.canUpdate: bound to its owner, the projectile updates exactly when the owner does.
    @Nullable
    private static LivingEntity ownerBoundTo(Entity entity) {
        return entity instanceof SpaceRipperStingyEyesEntity eyeBeam ? eyeBeam.getOwnerBoundTo() : null;
    }

    /**
     * {@link #shouldFreeze} for an entity in its own server level; false on the client and where time never stopped.
     */
    public static boolean shouldFreezeOnServer(Entity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
        return serverLevel.hasData(attachmentType) && serverLevel.getData(attachmentType).shouldFreeze(entity);
    }

    public static boolean shouldFreezeClientEntity(Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        LivingEntity boundOwner = ownerBoundTo(entity);
        if (boundOwner != null) {
            return shouldFreezeClientEntity(boundOwner);
        }
        return isTimeStoppedClientEntity(entity) && !canEntityMoveInStoppedTimeClient(entity);
    }

    private static boolean isTimeStoppedClientEntity(Entity entity) {
        return getClientDisplayInstance(new ChunkPos(entity.blockPosition())).isPresent();
    }

    private static boolean canEntityMoveInStoppedTimeClient(Entity entity) {
        if (!isTimeStoppedClientEntity(entity)) {
            return true;
        }
        if (TimeStopEntityMovementAuthorizers
                .canMoveInStoppedTime(entity)) {
            return true;
        }
        if (entity instanceof KnifeEntity knife) {
            return knife.canMoveInStoppedTime();
        }
        if (entity instanceof OwnerBoundProjectileEntity ownerBound) {
            if (ownerBound.canTickInStoppedTime()) {
                return true;
            }
            LivingEntity owner = ownerBound.getOwner();
            return owner != null && canEntityMoveInStoppedTimeClient(owner);
        }
        if (entity instanceof StandEntity stand && stand.getUser() != null) {
            return canEntityMoveInStoppedTimeClient(stand.getUser());
        }
        if (entity instanceof SoulEntity soul && soul.getOriginEntity() != null) {
            return canEntityMoveInStoppedTimeClient(soul.getOriginEntity());
        }
        // 1.16 updateEntityTimeStop in the client world: canPlayerMoveInStoppedTime(player, false), the game mode rule
        if (entity instanceof Player player && gameModeIgnoresTimeStop(player)) {
            return true;
        }
        if (JojoModConfig.getCommonConfigInstance(entity.level().isClientSide()).endermenBeyondTimeSpace.get()
                && ModInteractionUtil.isEntityEnderman(entity)) {
            return true;
        }
        return entity instanceof LivingEntity living && living.hasEffect(ModStatusEffects.TIME_STOP);
    }

    public boolean shouldInterruptEarly(Entity entity) {
        return shouldFreeze(entity)
                && !(entity instanceof net.minecraft.world.entity.item.ItemEntity);
    }

    public boolean interruptTickEarly(Entity entity) {
        if (!shouldInterruptEarly(entity)) {
            return false;
        }
        freezeEntity(entity);
        applyFrozenPosition(entity);
        applyInterruptedFreezeState(entity);
        return true;
    }

    public void freezeEntity(Entity entity) {
        if (!isFreezable(entity) || frozenEntities.containsKey(entity.getId())) {
            return;
        }
        // The motion stays on the entity, as in 1.16 (a stopped entity just did not tick): knockback and pushes
        // taken in stopped time stack onto it and the entity flies when time resumes.
        frozenEntities.put(entity.getId(), new FrozenEntityState(
                entity.position(),
                entity.fallDistance,
                entity instanceof Mob mob ? keepOldNoAi(mob) : null));
        entity.fallDistance = 0;
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
    }

    // A mob back from disk in stopped time reads no AI from the stop before; what it was is on the mob, as 1.16 PrevNoAi.
    private static boolean keepOldNoAi(Mob mob) {
        var saved = ModDataAttachmentTypes.TIME_STOP_PREV_NO_AI.get();
        if (mob.hasData(saved)) {
            return mob.getData(saved);
        }
        boolean wasNoAi = mob.isNoAi();
        mob.setData(saved, wasNoAi);
        return wasNoAi;
    }

    /**
     * A mob saved while frozen joins its level again, its chunk back or the server up after a crash: with no stop to hold
     * it, it gets its old AI back here. A stop that still holds it freezes it again and restores it as it ends.
     */
    @ApiStatus.Internal
    public static void restoreLoadedMobIfNoStopHoldsIt(Mob mob, ServerLevel level) {
        var saved = ModDataAttachmentTypes.TIME_STOP_PREV_NO_AI.get();
        if (!mob.hasData(saved)) {
            return;
        }
        var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
        if (!level.hasData(attachmentType) || !level.getData(attachmentType).shouldFreeze(mob)) {
            mob.setNoAi(mob.getData(saved));
            mob.removeData(saved);
        }
    }

    @ApiStatus.Internal
    public void updateGrabbedEntityPosition(LivingEntity entity) {
        if (entity.level() != level || !shouldFreeze(entity)) {
            return;
        }
        freezeEntity(entity);
        FrozenEntityState state = frozenEntities.get(entity.getId());
        if (state != null) {
            // Holding an entity replaces its old momentum, but not its original AI state.
            entity.setDeltaMovement(Vec3.ZERO);
            frozenEntities.put(entity.getId(), new FrozenEntityState(
                    entity.position(), 0.0F, state.wasNoAi()));
        }
    }

    /**
     * For an add-on that moves an entity of this level on purpose while time may be stopped around it (a rewind, a
     * dodge, a teleport): call it right after the move. While the entity stays frozen in stopped time, the freeze
     * holds it where it now is (it put it back where it froze on every tick before); its motion, fall distance and AI
     * state stay as they were frozen. An entity the move took out of stopped time (or out of every stop's reach) is
     * released at once, as time resuming releases it. Server side; nothing happens to an entity no stop covers.
     * @return whether the entity stays frozen
     */
    public boolean repinOrRelease(Entity entity) {
        if (entity.level() != level) {
            return false;
        }
        if (!shouldFreeze(entity)) {
            unfreezeEntity(entity);
            return false;
        }
        FrozenEntityState state = frozenEntities.get(entity.getId());
        if (state == null) {
            freezeEntity(entity);
        }
        else {
            frozenEntities.put(entity.getId(), new FrozenEntityState(entity.position(), state.fallDistance(), state.wasNoAi()));
        }
        return frozenEntities.containsKey(entity.getId());
    }

    /**
     * Rule G1, for a rewind (RewindAppliedEvent): ends every stop of this level that began after the rewind's point
     * and whose chunks meet its region, as an interrupted stop ends; older stops go on.
     * @return how many stops it ended
     */
    public int endStopsBegunAfter(long rewindPoint, AABB region) {
        List<Integer> ended = new ArrayList<>();
        for (Instance instance : instances.values()) {
            if (endsOnRewind(instance, rewindPoint, region)) {
                ended.add(instance.id());
            }
        }
        for (int id : ended) {
            removeInstance(id, RemovalReason.INTERRUPTED);
        }
        return ended.size();
    }

    /**
     * Rule G1 for one stop of this level: whether a rewind to this point in this region ends it, as
     * {@link #endStopsBegunAfter} decides. For a rewind that must know before it posts its RewindAppliedEvent which
     * stops go on (what they hold stays as it is).
     */
    public boolean endsOnRewind(Instance instance, long rewindPoint, AABB region) {
        return getStartGameTime(instance) > rewindPoint && meetsRegion(instance, region);
    }

    /**
     * The level's game time the stop began stopping time: when it was first committed, a held stop's opening included
     * (its ticksPassed starts below 0 there). Server side.
     */
    public long getStartGameTime(Instance instance) {
        Long started = timeStopStartTimes.get(instance.id());
        return started != null ? started : level.getGameTime() - Math.max(instance.ticksPassed(), 0);
    }

    private static boolean meetsRegion(Instance instance, AABB region) {
        if (instance.chunkRange() <= 0) {
            return true;
        }
        int reach = instance.chunkRange() - 1;
        ChunkPos center = instance.centerPos();
        return (center.x - reach) * 16.0 < region.maxX && (center.x + reach + 1) * 16.0 > region.minX
                && (center.z - reach) * 16.0 < region.maxZ && (center.z + reach + 1) * 16.0 > region.minZ;
    }

    /** {@link #repinOrRelease} in the entity's own server level; false on the client and where time never stopped. */
    public static boolean repinOrReleaseOnServer(Entity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
        return serverLevel.hasData(attachmentType) && serverLevel.getData(attachmentType).repinOrRelease(entity);
    }

    /**
     * Knockback on a stopped entity adds up on its waiting motion instead of halving it (1.16 stackKnockbackInstead).
     * An angled hit keeps its angle: 1.16 punches knocked back by strength * cos(xRot) and added strength * -sin(xRot)
     * upwards on their own, which the port does after the vanilla knockback a stopped entity never reaches.
     */
    @ApiStatus.Internal
    public static void stackKnockbackWhileStopped(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        float strength = event.getStrength();
        // not a hit the guard passes to the Stand: that knockback reaches the user already changed
        DamageSource source = DamageSourceModified.currentKnockbackSource(target);
        if (source instanceof DamageSourceModified angled && angled.jojo_ripples$knockbackXRotDeg() != 0) {
            angled.jojo_ripples$setKnockbackXRotAppliedStrength(strength);
            strength = angledKnockbackHorizontal(strength, angled.jojo_ripples$knockbackXRotDeg());
        }
        event.setCanceled(true);
        DamageUtil.applyKnockbackStack(target, strength, event.getRatioX(), event.getRatioZ());
        DamageSourceModified.afterKnockbackApplied(target, source);
        lastStackedKnockback = new WeakReference<>(event);
    }

    // the knockback event stackKnockbackWhileStopped last took over; weak, so it keeps no entity alive
    private static WeakReference<LivingKnockBackEvent> lastStackedKnockback = new WeakReference<>(null);

    /**
     * Whether stackKnockbackWhileStopped took this knockback over. 1.16 DamageUtil.knockback3d still added its 3D push
     * to a knockback stacked on a stopped target (LivingUtilCap.didStackKnockbackInstead).
     */
    @ApiStatus.Internal
    public static boolean stackedKnockbackInstead(LivingKnockBackEvent event) {
        return event != null && lastStackedKnockback.get() == event;
    }

    /** 1.16 StandEntityPunch: the ground part of an angled knockback, none at a right angle. */
    static float angledKnockbackHorizontal(float strength, float xRotDeg) {
        return Math.abs(xRotDeg) < 90F ? strength * Mth.cos(xRotDeg * MathUtil.DEG_TO_RAD) : 0;
    }

    public void unfreezeEntity(Entity entity) {
        FrozenEntityState state = frozenEntities.remove(entity.getId());
        if (state == null) {
            return;
        }
        entity.fallDistance = state.fallDistance();
        if (entity instanceof Mob mob && state.wasNoAi() != null) {
            mob.setNoAi(state.wasNoAi());
            mob.removeData(ModDataAttachmentTypes.TIME_STOP_PREV_NO_AI.get());
        }
        runQueuedOnTimeResume(entity);
    }

    public void queueOnTimeResume(Entity entity, Runnable action) {
        if (entity == null || action == null) {
            return;
        }
        if (!shouldFreeze(entity)) {
            action.run();
            return;
        }
        onTimeResume.computeIfAbsent(entity.getId(), ignored -> new ArrayList<>()).add(action);
    }

    private void runQueuedOnTimeResume(Entity entity) {
        List<Runnable> queued = onTimeResume.remove(entity.getId());
        if (queued != null) {
            for (Runnable action : queued) {
                action.run();
            }
        }
    }

    public void reconcileFrozenEntity(Entity entity) {
        if (entity.level() == level && entity.getEntityData().isDirty() && !playersVisionFrozen.isEmpty()) {
            dirtyEntityData.add(entity.getId());
        }
        if (!isFreezable(entity)) {
            unfreezeEntity(entity);
            return;
        }
        if (shouldFreeze(entity)) {
            freezeEntity(entity);
            FrozenEntityState state = frozenEntities.get(entity.getId());
            if (state != null) {
                entity.setPos(state.position());
                applyInterruptedFreezeState(entity);
            }
        }
        else {
            unfreezeEntity(entity);
        }
    }

    public void refreshTimeStopEffectState(LivingEntity entity) {
        if (entity.level() != level) {
            return;
        }
        reconcileFrozenEntity(entity);
        if (entity instanceof ServerPlayer player) {
            if (isTimeStopped(player)) {
                sendPlayerAwareness(player);
            }
            else {
                sendPlayerAwarenessRestore(player);
            }
        }
    }

    public void removeInstance(int id) {
        removeInstance(id, RemovalReason.EXPLICIT);
    }

    public void removeInstance(int id, RemovalReason reason) {
        Objects.requireNonNull(reason);
        Instance removed = instances.remove(id);
        if (removed != null) {
            applyTimeStopCooldown(removed);
            removeTimeStopEffectIfNoActiveInstance(removed);
            reconcileFrozenEntities();
            restoreTimeStopGamerulesIfNoActiveInstances();
            syncRemovedInstanceToAll(id);
            updateAllPlayerAwareness();
            pruneViewersIfNoStopLeft();
            ModEventHooks.onTimeStopRemoved(level, removed, reason);
        }
    }

    // a viewer gone before the last stop ended meets neither the awareness pass above nor the data sync, which needs a stop
    private void pruneViewersIfNoStopLeft() {
        if (instances.isEmpty() && !playersVisionFrozen.isEmpty()) {
            pruneFrozenVisionPlayers();
        }
    }

    // 1.16 TimeStopHandler.reset (the last player leaving) only dropped the stops: no cooldown or training. The
    // owner's early-end refund still gives back the unused share of each start.
    public void reset() {
        List<Instance> removedInstances = new ArrayList<>(instances.values());
        instances.clear();
        for (Instance removed : removedInstances) {
            LivingEntity user = getLivingEntityById(removed.userId());
            StandPower power = user != null ? PowerClass.STAND.get(user) : null;
            StartCharge charge = timeStopStartCharges.get(removed.id());
            if (power != null && power.hasPower() && charge != null) {
                refundUnusedTimeStopStartCost(power, removed, Math.max(removed.ticksPassed(), 0), charge);
            }
        }
        timeStopStarters.clear();
        timeStopStartCharges.clear();
        timeStopStartTimes.clear();
        for (Instance removed : removedInstances) {
            removeTimeStopEffectIfNoActiveInstance(removed);
        }
        reconcileFrozenEntities();
        restoreTimeStopGamerulesIfNoActiveInstances();
        for (Instance removed : removedInstances) {
            syncRemovedInstanceToAll(removed.id());
        }
        updateAllPlayerAwareness();
        pruneFrozenVisionPlayers();
        for (Instance removed : removedInstances) {
            ModEventHooks.onTimeStopRemoved(
                    level, removed, RemovalReason.RESET);
        }
    }

    /**
     * 1.16 TimeResume: the first press leaves 11 ticks, so the resume sound plays on the next tick and time resumes
     * about 0.55 s after the press. A second press, or a first one with 11 ticks or fewer left, resumes at once.
     */
    public boolean requestManualResume(int id) {
        Instance instance = instances.get(id);
        if (instance == null || !instance.isActive()) {
            return false;
        }
        int ticks = manualResumeTicks(instance);
        // 1.16 setTicksLeft: a stop first cut from over 30 ticks to under 30 still says its resume line
        boolean forceResumeVoiceLine = !instance.ticksManuallySet()
                && instance.ticksLeft() > Instance.TIME_RESUME_VOICELINE_TICKS
                && ticks < Instance.TIME_RESUME_VOICELINE_TICKS;
        Instance updated = instance.withTicksLeft(ticks, true, forceResumeVoiceLine);
        if (ticks <= 0 && instance.ticksLeft() > Instance.TIME_RESUME_SOUND_TICKS) {
            // resumed before the resume sound's tick (11 left, or two presses in one tick): play it now
            playResumeSound(updated, true);
        }
        instances.put(id, updated);
        syncInstanceToAll(updated);
        return true;
    }

    /** 1.16 TimeResume.perform: 11 ticks on the first press, 0 on a second one or with 11 or fewer left. */
    static int manualResumeTicks(Instance instance) {
        return !instance.ticksManuallySet() && instance.ticksLeft() > Instance.TIME_RESUME_FIRST_CLICK_TICKS
                ? Instance.TIME_RESUME_FIRST_CLICK_TICKS
                : 0;
    }

    public void tickLifecycle() {
        List<PendingRemoval> pendingRemovals = new ArrayList<>();
        var iter = instances.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            Instance instance = entry.getValue().tickDown();
            boolean manualResume = instance.ticksManuallySet()
                    && instance.ticksLeft() <= 0;
            boolean shouldEnd = !manualResume
                    && shouldEndTimeStop(instance);
            if (instance.ticksLeft() > 0 && !shouldEnd) {
                boolean forcedResumeVoiceLinePlayed = playResumeVoiceLine(instance);
                if (forcedResumeVoiceLinePlayed) {
                    instance = instance.withForceResumeVoiceLine(false);
                }
                playResumeSound(instance);
                applySprintFloat(instance);
                entry.setValue(instance);
            }
            else {
                if (!shouldEnd && instance.forceResumeVoiceLine()) {
                    playResumeVoiceLine(instance);
                }
                Instance expired = instance;
                iter.remove();
                RemovalReason reason = shouldEnd
                        ? RemovalReason.INTERRUPTED
                        : expired.ticksManuallySet()
                                ? RemovalReason.MANUAL_RESUME
                                : RemovalReason.EXPIRED;
                pendingRemovals.add(
                        new PendingRemoval(expired, reason));
            }
        }
        if (!pendingRemovals.isEmpty()) {
            applyTimeStopCooldowns(pendingRemovals.stream()
                    .map(PendingRemoval::instance)
                    .toList());
            for (PendingRemoval removal : pendingRemovals) {
                removeTimeStopEffectIfNoActiveInstance(
                        removal.instance());
            }
            reconcileFrozenEntities();
            restoreTimeStopGamerulesIfNoActiveInstances();
            for (PendingRemoval removal : pendingRemovals) {
                syncRemovedInstanceToAll(removal.instance().id());
            }
            updateAllPlayerAwareness();
        }
        if (!instances.isEmpty() && !playersVisionFrozen.isEmpty()) {
            manualEntitiesDataSync();
        }
        pruneViewersIfNoStopLeft();
        for (PendingRemoval removal : pendingRemovals) {
            ModEventHooks.onTimeStopRemoved(
                    level, removal.instance(), removal.reason());
        }
    }

    private void applySprintFloat(Instance instance) {
        ServerPlayer player = getServerPlayerByEntityId(instance.userId());
        if (player == null || instance.isStartupSettling() || !canPlayerMoveInStoppedTime(player)
                || !instance.covers(new ChunkPos(player.blockPosition()))) {
            clearTimeStopFloat(player);
            return;
        }
        applyTimeStopFloat(player, isTimeStopFloatInput(player));
    }

    public static boolean applyTimeStopFloat(Player player, boolean inputHeld) {
        PlayerMovementInputData input = PlayerMovementInputData.get(player);
        if (input == null) {
            return false;
        }
        if (!inputHeld || player.getAbilities().flying || player.onGround()) {
            input.timeStopFloatActive = false;
            return false;
        }
        Vec3 motion = player.getDeltaMovement();
        if (!input.timeStopFloatActive) {
            if (motion.y > TIME_STOP_SPRINT_FLOAT_START_THRESHOLD) {
                return false;
            }
            input.timeStopFloatActive = true;
        }
        double upwardVelocity = roundaboutTimeStopFloatVelocity(motion.y);
        player.setDeltaMovement(motion.x, upwardVelocity, motion.z);
        player.hasImpulse = true;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.hurtMarked = true;
        }
        player.fallDistance = 0.0F;
        return true;
    }

    private static double roundaboutTimeStopFloatVelocity(double currentYVelocity) {
        double liftVelocity = TIME_STOP_SPRINT_FLOAT_LIFT_VELOCITY * TIME_STOP_SPRINT_FLOAT_LIFT_MULTIPLIER;
        double steadyVelocity = TIME_STOP_SPRINT_FLOAT_STEADY_VELOCITY * TIME_STOP_SPRINT_FLOAT_LIFT_MULTIPLIER;
        double upwardVelocity = liftVelocity;
        double recoveredVelocity = currentYVelocity + liftVelocity;
        if (recoveredVelocity >= steadyVelocity) {
            upwardVelocity = steadyVelocity;
        }
        return Math.max(currentYVelocity, upwardVelocity);
    }

    public static double travelGravityForTimeStopFloat(Player player, double gravity) {
        PlayerMovementInputData input = PlayerMovementInputData.get(player);
        if (input != null && input.timeStopFloatActive && !player.onGround() && !player.getAbilities().flying) {
            player.fallDistance = 0.0F;
            return 0.0D;
        }
        return gravity;
    }

    public static void clearTimeStopFloat(Player player) {
        PlayerMovementInputData input = PlayerMovementInputData.get(player);
        if (input != null) {
            input.timeStopFloatActive = false;
        }
    }

    private static boolean isTimeStopFloatInput(Player player) {
        PlayerMovementInputData input = PlayerMovementInputData.get(player);
        return input != null && input.sprint && input.jumping;
    }

    private void updateFrozenVisionState(ServerPlayer player, boolean canSee) {
        if (canSee) {
            unfreezePlayerVision(player);
        }
        else {
            if (playersVisionFrozen.add(player.getUUID()) && playersVisionFrozen.size() == 1) {
                // Capture data that was already dirty when the first viewer froze.
                for (Entity entity : level.getAllEntities()) {
                    if (entity.getEntityData().isDirty()) {
                        dirtyEntityData.add(entity.getId());
                    }
                }
            }
        }
    }

    private void unfreezePlayerVision(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (removeFrozenViewer(playerId) || delayedEntityData.containsKey(playerId)) {
            sendDataOnTimeStopUnfreeze(player);
        }
    }

    private void clearFrozenVisionForPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        removeFrozenViewer(playerId);
        delayedEntityData.remove(playerId);
    }

    private boolean removeFrozenViewer(UUID playerId) {
        boolean removed = playersVisionFrozen.remove(playerId);
        if (playersVisionFrozen.isEmpty()) {
            dirtyEntityData.clear();
        }
        return removed;
    }

    @ApiStatus.Internal
    public static void onEntityDataDirty(Entity entity) {
        if (entity.level() instanceof ServerLevel level && level.getServer().isSameThread()) {
            var key = ModDataAttachmentTypes.TIME_STOP.get();
            if (level.hasData(key)) {
                TimeStopState state = level.getData(key);
                if (!state.playersVisionFrozen.isEmpty()) {
                    state.dirtyEntityData.add(entity.getId());
                }
            }
        }
    }

    private void manualEntitiesDataSync() {
        pruneFrozenVisionPlayers();
        if (dirtyEntityData.isEmpty()) {
            return;
        }
        int[] pending = dirtyEntityData.toIntArray();
        dirtyEntityData.clear();
        for (int entityId : pending) {
            Entity entity = level.getEntity(entityId);
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            SynchedEntityData entityData = entity.getEntityData();
            if (!entityData.isDirty()) {
                continue;
            }
            List<ServerPlayer> trackingPlayers = NetworkUtil.getTrackingPlayers(entity)
                    .map(connection -> connection.getPlayer())
                    .toList();
            if (trackingPlayers.stream().noneMatch(player -> playersVisionFrozen.contains(player.getUUID()))) {
                // A viewer may start tracking this still-dirty entity before the stop ends.
                dirtyEntityData.add(entityId);
                continue;
            }
            List<SynchedEntityData.DataValue<?>> packedData = entityData.packDirty();
            if (packedData == null || packedData.isEmpty()) {
                continue;
            }
            for (ServerPlayer tracking : trackingPlayers) {
                if (playersVisionFrozen.contains(tracking.getUUID())) {
                    addDataForTimeStopUnfreeze(tracking, entity, packedData);
                }
                else {
                    PacketDistributor.sendToPlayer(tracking, new TrDirectEntityDataPacket(entity.getId(), packedData));
                }
            }
        }
    }

    private void addDataForTimeStopUnfreeze(ServerPlayer player, Entity entity, List<SynchedEntityData.DataValue<?>> newData) {
        Map<Integer, Map<Integer, SynchedEntityData.DataValue<?>>> entitiesData = delayedEntityData
                .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        Map<Integer, SynchedEntityData.DataValue<?>> dataByAccessor = entitiesData
                .computeIfAbsent(entity.getId(), ignored -> new HashMap<>());
        for (SynchedEntityData.DataValue<?> dataValue : newData) {
            dataByAccessor.put(dataValue.id(), dataValue);
        }
    }

    private void sendDataOnTimeStopUnfreeze(ServerPlayer player) {
        Map<Integer, Map<Integer, SynchedEntityData.DataValue<?>>> entitiesData = delayedEntityData.remove(player.getUUID());
        if (entitiesData == null || entitiesData.isEmpty()) {
            return;
        }
        for (var entry : entitiesData.entrySet()) {
            Map<Integer, SynchedEntityData.DataValue<?>> data = entry.getValue();
            if (!data.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new TrDirectEntityDataPacket(entry.getKey(), new ArrayList<>(data.values())));
            }
        }
    }

    private void pruneFrozenVisionPlayers() {
        playersVisionFrozen.removeIf(playerId -> !isPlayerInLevel(playerId));
        delayedEntityData.keySet().removeIf(playerId -> !isPlayerInLevel(playerId));
        if (playersVisionFrozen.isEmpty()) {
            dirtyEntityData.clear();
        }
    }

    private boolean isPlayerInLevel(UUID playerId) {
        for (ServerPlayer player : level.players()) {
            if (player.getUUID().equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    private void freezeTimeStopGamerulesIfNeeded() {
        MinecraftServer server = level.getServer();
        if (gamerulesFrozen || hasActiveTimeStopInAnyLevel(server)) {
            return;
        }
        GameRules gameRules = server.overworld().getGameRules();
        previousDaylightCycle = gameRules.getBoolean(GameRules.RULE_DAYLIGHT);
        gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        previousWeatherCycle = gameRules.getBoolean(GameRules.RULE_WEATHER_CYCLE);
        gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        gamerulesFrozen = true;
    }

    private void restoreTimeStopGamerulesIfNoActiveInstances() {
        MinecraftServer server = level.getServer();
        if (!gamerulesFrozen || hasActiveTimeStopInAnyLevel(server)) {
            return;
        }
        GameRules gameRules = server.overworld().getGameRules();
        gameRules.getRule(GameRules.RULE_DAYLIGHT).set(previousDaylightCycle, server);
        gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(previousWeatherCycle, server);
        gamerulesFrozen = false;
    }

    private static boolean hasActiveTimeStopInAnyLevel(MinecraftServer server) {
        var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
        for (ServerLevel serverLevel : server.getAllLevels()) {
            if (serverLevel.hasData(attachmentType)
                    && serverLevel.getData(attachmentType).instances.values().stream().anyMatch(Instance::isActive)) {
                return true;
            }
        }
        return false;
    }

    private void reconcileFrozenEntities() {
        for (Integer entityId : new ArrayList<>(frozenEntities.keySet())) {
            Entity entity = level.getEntity(entityId);
            if (entity == null || entity.isRemoved()) {
                frozenEntities.remove(entityId);
                onTimeResume.remove(entityId);
                continue;
            }
            if (shouldFreeze(entity)) {
                FrozenEntityState state = frozenEntities.get(entityId);
                if (state != null) {
                    entity.setPos(state.position());
                }
                applyInterruptedFreezeState(entity);
            }
            else {
                unfreezeEntity(entity);
            }
        }
    }

    private void reconcileEntitiesForInstance(Instance instance) {
        for (Entity entity : UtilFunctions.getEntities(level)) {
            if (instance.covers(new ChunkPos(entity.blockPosition()))) {
                reconcileFrozenEntity(entity);
            }
        }
    }

    private void applyTimeStopCooldown(Instance removed) {
        applyTimeStopCooldowns(List.of(removed));
    }

    private void applyTimeStopCooldowns(List<Instance> removedInstances) {
        List<TimeStopSettlement> settlements =
                new ArrayList<>(removedInstances.size());
        Map<StandPower, Integer> cooldownTicks =
                new IdentityHashMap<>();
        Map<StandPower, String> cooldownAbilities =
                new IdentityHashMap<>();
        for (Instance removed : removedInstances) {
            LivingEntity user = getLivingEntityById(removed.userId());
            StandPower power =
                    user != null ? PowerClass.STAND.get(user) : null;
            if (power != null && power.hasPower()) {
                int effectiveTicksPassed =
                        Math.max(removed.ticksPassed(), 0);
                settlements.add(new TimeStopSettlement(
                        power, removed, effectiveTicksPassed,
                        getStartingAbility(removed, power),
                        timeStopStartCharges.get(removed.id())));
                Integer previousTicks = cooldownTicks.get(power);
                if (previousTicks == null || effectiveTicksPassed > previousTicks) {
                    cooldownTicks.put(power, effectiveTicksPassed);
                    cooldownAbilities.put(power, getCooldownAbilityName(removed, power));
                }
            }
        }
        for (Instance removed : removedInstances) {
            if (!instances.containsKey(removed.id())) {
                timeStopStarters.remove(removed.id());
                timeStopStartCharges.remove(removed.id());
                timeStopStartTimes.remove(removed.id());
            }
        }
        for (TimeStopSettlement settlement : settlements) {
            refundUnusedTimeStopStartCost(
                    settlement.power(),
                    settlement.instance(),
                    settlement.effectiveTicksPassed(),
                    settlement.startCharge());
        }
        // 1.16 TimeStopInstance.onRemoved: the learning goes to the time stop that started it.
        for (TimeStopSettlement settlement : settlements) {
            TimeStopLearning.onTimeStopEnded(
                    settlement.power(),
                    settlement.timeStop(),
                    settlement.effectiveTicksPassed());
        }
        for (Map.Entry<StandPower, Integer> cooldown
                : cooldownTicks.entrySet()) {
            TimeStopCooldowns.setTimeStopCooldownsOnTimeStopEnd(
                    cooldown.getKey(),
                    cooldownAbilities.get(cooldown.getKey()),
                    cooldown.getValue());
        }
    }

    /**
     * 1.16 TimeStopInstance.onRemoved put the cooldown on the TimeStop action that
     * started the instance, so an add-on time stop is cooled down under its own name.
     */
    private String getCooldownAbilityName(Instance removed, StandPower power) {
        AbilityId starter = timeStopStarters.get(removed.id());
        if (starter != null && starter.powerTypeId() != null) {
            return starter.nameInMoveset();
        }
        Ability timeStop = getMovesetTimeStopAbility(power);
        return timeStop != null ? timeStop.name() : TimeStopCooldowns.TIME_STOP;
    }

    /**
     * The ability whose unlock keeps the instance running. 1.16 TimeStopInstance.tick
     * checked the action that started it, not a fixed name, so Shadow The World's
     * "shadow_world_time_stop" and Catch the Rainbow's rain stop keep their instance.
     */
    @Nullable
    private Ability getStartingAbility(Instance instance, StandPower power) {
        AbilityId starter = timeStopStarters.get(instance.id());
        if (starter == null || starter.powerTypeId() == null) {
            return getMovesetTimeStopAbility(power);
        }
        Ability ability = power.getMoveset().getAbility(starter.nameInMoveset());
        return ability != null && starter.equals(ability.abilityId) ? ability : null;
    }

    /**
     * For instances started without an ability (API callers): the moveset's "time_stop",
     * otherwise its first time-stop ability under any name.
     */
    @Nullable
    private static Ability getMovesetTimeStopAbility(StandPower power) {
        Ability timeStop = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
        if (timeStop != null) {
            return timeStop;
        }
        for (Ability ability : power.getMoveset().abilities.values()) {
            if (ability instanceof TimeStopAbility) {
                return ability;
            }
        }
        return null;
    }

    /**
     * The time stop a Stand trains and is costed by when no ability is at hand.
     */
    @ApiStatus.Internal
    @Nullable
    public static Ability getDefaultTimeStopAbility(@Nullable StandPower power) {
        return power != null ? getMovesetTimeStopAbility(power) : null;
    }

	private void refundUnusedTimeStopStartCost(StandPower power, Instance removed, int effectiveTicksPassed,
			@Nullable StartCharge startCharge) {
		if (!removed.refundUnusedStartCost()) {
			return;
		}
		if (power.isStaminaInfinite() || startCharge == null) {
			return;
		}
		// a share of what the start took (policy and multiplier included), never repriced at the end
		float refund = getUnusedStartCostRefund(startCharge.charged(), startCharge.unrefundable(),
				removed.totalTicks(), effectiveTicksPassed);
		if (refund > 0.0F) {
			power.setStamina(power.getStamina() + refund);
		}
	}

	/**
	 * (charged - unrefundable) x unused ticks / total ticks, never below 0; nothing without a recorded charge.
	 * The owner's example: a full 200-tick stop that cost 1000, whose shortest stop costs 100, resumed after 10 ticks
	 * gives back 900 x 190 / 200 = 855; the shortest stop itself gives back nothing.
	 */
	static float getUnusedStartCostRefund(float chargedStartCost, float unrefundableStartCost, int totalTicks,
			int effectiveTicksPassed) {
		if (!(chargedStartCost > 0.0F) || !Float.isFinite(chargedStartCost) || totalTicks <= 0
				|| !Float.isFinite(unrefundableStartCost)) {
			return 0.0F;
		}
		float refundable = chargedStartCost - Math.max(unrefundableStartCost, 0.0F);
		int elapsedTicks = Math.max(0, Math.min(effectiveTicksPassed, totalTicks));
		if (!(refundable > 0.0F) || elapsedTicks >= totalTicks) {
			return 0.0F;
		}
		return refundable * (float) (totalTicks - elapsedTicks) / (float) totalTicks;
	}

    private void removeTimeStopEffectIfNoActiveInstance(Instance removed) {
        LivingEntity user = getLivingEntityById(removed.userId());
        if (user != null && user.hasEffect(ModStatusEffects.TIME_STOP)
                && instances.values().stream().noneMatch(instance -> instance.isActive() && instance.userId() == removed.userId())) {
            user.removeEffect(ModStatusEffects.TIME_STOP);
        }
    }

    private boolean shouldEndTimeStop(Instance instance) {
        LivingEntity user = getLivingEntityById(instance.userId());
        if (user == null || !user.isAlive()) {
            return true;
        }
        StandPower power = PowerClass.STAND.get(user);
        if (power == null || !power.hasPower()) {
            return true;
        }
        Ability timeStop = getStartingAbility(instance, power);
        if (timeStop == null || !timeStop.isAbilityUnlocked(power)) {
            return true;
        }
        if (instance.isStartupSettling()) {
            return false;
        }
        float staminaCostTick = instance.staminaCostTick();
        if (staminaCostTick > 0) {
            staminaCostTick *= PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power);
            staminaCostTick += power.getStaminaTickGain();
            if (!power.consumeStamina(staminaCostTick, true)) {
                power.setStamina(0);
                return true;
            }
        }
        return false;
    }

    private boolean playResumeVoiceLine(Instance instance) {
        if (instance.resumeVoiceLineUserId() < 0 || instance.totalTicks() < 100) {
            return false;
        }
        if (instance.ticksLeft() == Instance.TIME_RESUME_SOUND_TICKS) {
            return false;
        }
        if (instance.ticksLeft() != Instance.TIME_RESUME_VOICELINE_TICKS && !instance.forceResumeVoiceLine()) {
            return false;
        }
        LivingEntity resumeVoiceLineUser = getLivingEntityById(instance.resumeVoiceLineUserId());
        StandPower power = resumeVoiceLineUser != null ? PowerClass.STAND.get(resumeVoiceLineUser) : null;
        TimeStopAudioDecision decision = resumeVoiceLineUser != null
                ? TimeStopBehaviorPolicies.resolveAudio(
                        instance.standTypeId().orElse(null),
                        new TimeStopAudioContext(
                                TimeStopAudioCue.RESUME_VOICE,
                                resumeVoiceLineUser,
                                power,
                                null,
                                instance,
                                false))
                : TimeStopAudioDecision.silent();
        Holder<SoundEvent> voiceLine = switch (decision.kind()) {
            case PASS -> power != null
                    ? getResumeVoiceLine(instance, power)
                    : null;
            case SOUND -> decision.sound();
            case SILENT -> null;
        };
        if (resumeVoiceLineUser != null && power != null && voiceLine != null) {
            // 1.16 sayVoiceLine without interrupting: a line still being said keeps playing
            JojoModUtil.sayVoiceLine(
                    resumeVoiceLineUser,
                    voiceLine,
                    1.0F,
                    1.0F,
                    200,
                    false);
        }
        return instance.forceResumeVoiceLine();
    }

    @Nullable
    private Holder<SoundEvent> getResumeVoiceLine(Instance instance, StandPower power) {
        if (power.getPowerType() == ModStands.STAR_PLATINUM.get()) {
            return ModSoundEvents.JOTARO_TIME_RESUMES;
        }
		if (power.getPowerType() == ModStands.THE_WORLD.get()) {
			return instance.ticksManuallySet() ? ModSoundEvents.DIO_TIME_RESUMES : ModSoundEvents.DIO_TIMES_UP;
		}
		return null;
	}

	private void playResumeSound(Instance instance) {
        playResumeSound(instance, false);
    }

	private void playResumeSound(Instance instance, boolean force) {
        if (force
                || instance.ticksLeft()
                        == Instance.TIME_RESUME_SOUND_TICKS) {
            if (instance.resumeSoundUserId() < 0) {
                return;
            }
            LivingEntity user = getLivingEntityById(instance.userId());
            LivingEntity resumeSoundUser = getLivingEntityById(instance.resumeSoundUserId());
            StandPower power = resumeSoundUser != null ? PowerClass.STAND.get(resumeSoundUser) : null;
            if (user != null && power != null) {
                TimeStopAudioDecision decision =
                        TimeStopBehaviorPolicies.resolveAudio(
                                instance.standTypeId().orElse(null),
                                new TimeStopAudioContext(
                                        TimeStopAudioCue.RESUME_SOUND,
                                        resumeSoundUser,
                                        power,
                                        null,
                                        instance,
                                        false));
                Holder<SoundEvent> resumeSound = switch (decision.kind()) {
                    case PASS -> getTimeResumeSound(power);
                    case SOUND -> decision.sound();
                    case SILENT -> null;
                };
                if (resumeSound != null) {
                    StandUtil.broadcastSoundWithCondition(
                            level,
                            user.position(),
                            resumeSound,
                            false,
                            power,
                            SoundSource.AMBIENT,
                            5.0F,
                            1.0F,
                            player -> instance.covers(
                                            new ChunkPos(
                                                    player.blockPosition()))
                                    && canPlayerSeeInStoppedTime(player));
                }
            }
        }
    }

    private static Holder<SoundEvent> getTimeResumeSound(StandPower power) {
        return power != null && power.getPowerType() == ModStands.STAR_PLATINUM.get()
                ? ModSoundEvents.STAR_PLATINUM_TIME_RESUME
                : ModSoundEvents.THE_WORLD_TIME_RESUME;
    }

    public void syncInstanceToAll(Instance instance) {
        for (var player : level.players()) {
            PacketDistributor.sendToPlayer(player, timeStopInstancePacket(instance, false));
        }
    }

    private void syncAddedInstanceToAll(Instance instance) {
        for (var player : level.players()) {
            PacketDistributor.sendToPlayer(player, timeStopInstancePacket(instance, true));
            sendPlayerAwareness(player);
        }
    }

    public void syncRemovedInstanceToAll(int id) {
        for (var player : level.players()) {
            syncRemovedInstanceToPlayer(player, id);
        }
    }

    private void syncInstanceToPlayer(ServerPlayer player, Instance instance) {
        PacketDistributor.sendToPlayer(player, timeStopInstancePacket(instance, true));
    }

    private static TrTimeStopInstancePacket timeStopInstancePacket(Instance instance, boolean openingVisual) {
        return new TrTimeStopInstancePacket(
                instance.id(),
                instance.ticksLeft(),
                instance.totalTicks(),
                instance.centerPos().x,
                instance.centerPos().z,
                instance.chunkRange(),
                instance.userId(),
                instance.visualRoute(),
                instance.standTypeId(),
                instance.selectedSkin(),
                instance.resumeSoundUserId(),
                instance.resumeVoiceLineUserId(),
                instance.ticksManuallySet(),
                instance.forceResumeVoiceLine(),
                instance.staminaCostTick(),
                instance.ticksPassed(),
                instance.refundUnusedStartCost(),
                false,
                openingVisual);
    }

    private void syncRemovedInstanceToPlayer(ServerPlayer player, int id) {
        PacketDistributor.sendToPlayer(player, removedTimeStopInstancePacket(id));
    }

    private void clearClientInstancesForPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, removedTimeStopInstancePacket(-1));
    }

    private static TrTimeStopInstancePacket removedTimeStopInstancePacket(int id) {
        return new TrTimeStopInstancePacket(id, 0, 0, 0, 0, 0, -1, "", Optional.empty(), Optional.empty(), -1, -1, false, false, 0, 0, true, true, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
            if (level.hasData(attachmentType)) {
                TimeStopState state = level.getData(attachmentType);
                state.tickOnLevelTick();
            }
        }
    }

    @ApiStatus.Internal
    public void tickOnLevelTick() {
        if (!instances.isEmpty()) {
            tickLifecycle();
        }
        drainRedstoneIfTimeResumed();
    }

    // Every way a stop ends leaves the level here within a tick, ahead of that tick's block ticks and block events.
    private void drainRedstoneIfTimeResumed() {
        if (redstoneHold.hasWork() && !hasActiveStop()) {
            redstoneHold.drain();
        }
        if (scheduledTickHold.hasWork() && !hasActiveStop()) {
            scheduledTickHold.drain();
        }
    }

    /** A block or fluid tick scheduled into a stopped chunk: its delay counts from the stop's end (TimeStopScheduledTickHold). */
    @ApiStatus.Internal
    public static void noteScheduledTick(ServerLevel tickLevel, BlockPos pos, Object type, boolean fluid, long triggerTick) {
        var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
        if (tickLevel.hasData(attachmentType)) {
            TimeStopState state = tickLevel.getData(attachmentType);
            if (!state.instances.isEmpty() && state.isTimeStopped(new ChunkPos(pos))) {
                state.scheduledTickHold.noteScheduled(pos, type, fluid, triggerTick);
            }
        }
    }

    /** A block or fluid tick come due in a stopped chunk keeps the delay it had left when time stopped there. */
    @ApiStatus.Internal
    public void noteFrozenTickDue(BlockPos pos, Object type, boolean fluid) {
        ChunkPos chunkPos = new ChunkPos(pos);
        long stopStart = instances.values().stream()
                .filter(Instance::isActive)
                .filter(instance -> instance.covers(chunkPos))
                .mapToLong(this::getStartGameTime)
                .min()
                .orElse(level.getGameTime());
        scheduledTickHold.noteDue(pos, type, fluid, stopStart);
    }

    boolean hasActiveStop() {
        return instances.values().stream().anyMatch(Instance::isActive);
    }

    /** Whether stopped time keeps this neighbour update from its block until it resumes. */
    @ApiStatus.Internal
    public static boolean holdNeighborUpdate(Level updatedLevel, BlockState receiver, BlockPos pos, Block source, BlockPos sourcePos,
            boolean movedByPiston) {
        if (!(updatedLevel instanceof ServerLevel serverLevel)) {
            return false;
        }
        var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
        if (!serverLevel.hasData(attachmentType)) {
            return false;
        }
        TimeStopState state = serverLevel.getData(attachmentType);
        return !state.instances.isEmpty() && state.isTimeStopped(new ChunkPos(pos))
                && state.redstoneHold.hold(receiver, pos, source, sourcePos, movedByPiston);
    }

    /** How many entities' data changes wait for a frozen viewer. */
    @ApiStatus.Internal
    public int heldEntityDataCount() {
        return dirtyEntityData.size();
    }

    /** The held neighbour updates' blocks, in the order they will run. */
    @ApiStatus.Internal
    public List<BlockPos> heldNeighborUpdates() {
        return redstoneHold.heldPositions();
    }

    /**
     * No tick follows, and a level's block events are not saved: stops end as when the last player leaves, then what
     * waited for them runs now, so the save holds a world that was told.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
        for (ServerLevel serverLevel : event.getServer().getAllLevels()) {
            if (serverLevel.hasData(attachmentType)) {
                TimeStopState state = serverLevel.getData(attachmentType);
                if (!state.instances.isEmpty()) {
                    state.reset();
                }
                state.drainRedstoneIfTimeResumed();
                ((ServerLevelBlockEventsInvoker) serverLevel).jojo_ripples$runBlockEvents();
            }
        }
    }

    public static Optional<Instance> getClientInstance(int id) {
        return Optional.ofNullable(CLIENT_INSTANCES.get(id));
    }

    public static Optional<Instance> getClientDisplayInstance(ChunkPos localPlayerChunk) {
        Instance selected = null;
        for (Instance instance : CLIENT_INSTANCES.values()) {
            if (!instance.isActive() || !instance.covers(localPlayerChunk)) {
                continue;
            }
            if (selected == null || instance.ticksLeft() > selected.ticksLeft()) {
                selected = instance;
            }
        }
        return Optional.ofNullable(selected);
    }

    public static void putClientInstance(Instance instance) {
        Instance previous = CLIENT_INSTANCES.get(instance.id());
        if (previous != null && previous.isActive() && instance.isActive() && instance.ticksPassed() <= 0) {
            instance = new Instance(
                    instance.id(),
                    instance.ticksLeft(),
                    instance.totalTicks(),
                    instance.centerPos(),
                    instance.chunkRange(),
                    instance.userId(),
                    instance.visualRoute(),
                    instance.standTypeId(),
                    instance.selectedSkin(),
                    instance.resumeSoundUserId(),
                    instance.resumeVoiceLineUserId(),
                    instance.ticksManuallySet(),
                    instance.forceResumeVoiceLine(),
                    instance.staminaCostTick(),
                    previous.ticksPassed(),
                    instance.refundUnusedStartCost());
        }
        CLIENT_INSTANCES.put(instance.id(), instance);
    }

    public static void removeClientInstance(int id) {
        CLIENT_INSTANCES.remove(id);
    }

    public static void tickClientInstances() {
        var iter = CLIENT_INSTANCES.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            Instance ticked = entry.getValue().tickDown();
            if (ticked.isActive()) {
                entry.setValue(ticked);
            }
            else {
                iter.remove();
            }
        }
    }

    public static void clearClientInstances() {
        CLIENT_INSTANCES.clear();
    }

    public ServerLevel level() {
        return level;
    }

    private void applyInterruptedFreezeState(Entity entity) {
        entity.fallDistance = 0;
        if (entity instanceof LivingEntity living && living.invulnerableTime > 0) {
            living.invulnerableTime--;
        }
    }

    private void applyFrozenPosition(Entity entity) {
        FrozenEntityState state = frozenEntities.get(entity.getId());
        if (state != null) {
            entity.setPos(state.position());
        }
    }

    private boolean isFreezable(Entity entity) {
        return !(entity instanceof LivingEntity living && living.hasEffect(ModStatusEffects.TIME_STOP))
                && !entity.isRemoved();
    }

    public static boolean canPlayerSeeInStoppedTime(ServerPlayer player) {
        return canPlayerSeeInStoppedTime(player, canPlayerMoveInStoppedTime(player));
    }

    private static boolean canPlayerSeeInStoppedTime(ServerPlayer player, boolean canMove) {
        return canMove
                || hasTimeStopAbility(player)
                || TimeStopAwarenessProviders.resolve(player).canSee();
    }

    private static boolean canPlayerMoveInStoppedTime(ServerPlayer player) {
        return player.hasEffect(ModStatusEffects.TIME_STOP)
                || gameModeIgnoresTimeStop(player)
                || player.server.isSingleplayerOwner(player.getGameProfile())
                || TimeStopAwarenessProviders.resolve(player).canMove();
    }

    public static boolean gameModeIgnoresTimeStop(Player player) {
        return JojoModUtil.getActualGameModeWhilePossessing(player)
                .map(gameMode -> gameMode == GameType.CREATIVE || gameMode == GameType.SPECTATOR)
                .orElseGet(() -> player.isCreative() || player.isSpectator());
    }

    private boolean canEntityMoveInStoppedTime(Entity entity) {
        if (!isTimeStopped(entity)) {
            return true;
        }
        if (TimeStopEntityMovementAuthorizers
                .canMoveInStoppedTime(entity)) {
            return true;
        }
        if (entity instanceof KnifeEntity knife) {
            return knife.canMoveInStoppedTime();
        }
        if (entity instanceof OwnerBoundProjectileEntity ownerBound) {
            if (ownerBound.canTickInStoppedTime()) {
                return true;
            }
            LivingEntity owner = ownerBound.getOwner();
            return owner != null && canEntityMoveInStoppedTime(owner);
        }
        if (entity instanceof StandEntity stand && stand.getUser() != null) {
            return canEntityMoveInStoppedTime(stand.getUser());
        }
        if (entity instanceof SoulEntity soul && soul.getOriginEntity() != null) {
            return canEntityMoveInStoppedTime(soul.getOriginEntity());
        }
        if (entity instanceof ServerPlayer player) {
            return canPlayerMoveInStoppedTime(player);
        }
        if (JojoModConfig.getCommonConfigInstance(entity.level().isClientSide()).endermenBeyondTimeSpace.get()
                && ModInteractionUtil.isEntityEnderman(entity)) {
            return true;
        }
        return entity instanceof LivingEntity living && living.hasEffect(ModStatusEffects.TIME_STOP);
    }

    private static boolean hasTimeStopAbility(LivingEntity entity) {
        StandPower standPower = PowerClass.STAND.get(entity);
        if (standPower == null || !standPower.hasPower()) {
            return false;
        }
        return standPower.getMoveset().abilities.values().stream()
                .anyMatch(ability -> ability.canUserSeeInStoppedTime(entity, standPower)
                        && ability.isAbilityUnlocked(standPower));
    }

    public record Awareness(boolean canSee, boolean canMove) {
        public static final Awareness FREE = new Awareness(true, true);
    }

    private record FrozenEntityState(Vec3 position, float fallDistance, @Nullable Boolean wasNoAi) {}

    private record PendingRemoval(
            Instance instance, RemovalReason reason) {}

    private record TimeStopSettlement(
            StandPower power,
            Instance instance,
            int effectiveTicksPassed,
            @Nullable Ability timeStop,
            @Nullable StartCharge startCharge) {}

    private record StartCharge(float charged, float unrefundable) {}

    public static record Instance(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, int userId, String visualRoute, Optional<ResourceLocation> standTypeId, Optional<ResourceLocation> selectedSkin, int resumeSoundUserId, int resumeVoiceLineUserId, boolean ticksManuallySet, boolean forceResumeVoiceLine, float staminaCostTick, int ticksPassed, boolean refundUnusedStartCost) {
        public static final int TIME_RESUME_SOUND_TICKS = 10;
        public static final int TIME_RESUME_VOICELINE_TICKS = 30;
        public static final int TIME_RESUME_FIRST_CLICK_TICKS = TIME_RESUME_SOUND_TICKS + 1;

        public Instance {
            standTypeId = standTypeId != null ? standTypeId : Optional.empty();
            selectedSkin = selectedSkin != null ? selectedSkin : Optional.empty();
        }

        public Instance(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, int userId, String visualRoute, Optional<ResourceLocation> standTypeId, Optional<ResourceLocation> selectedSkin, int resumeSoundUserId, int resumeVoiceLineUserId, boolean ticksManuallySet, boolean forceResumeVoiceLine, float staminaCostTick, int ticksPassed) {
            this(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed, true);
        }

        public Instance(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, int userId, String visualRoute) {
            this(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, Optional.empty(), Optional.empty(), userId, userId, false, false, 0, 0);
        }

        public Instance(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, int userId, String visualRoute, float staminaCostTick) {
            this(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, Optional.empty(), Optional.empty(), userId, userId, false, false, staminaCostTick, 0);
        }

        public Instance(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, int userId, String visualRoute, int resumeSoundUserId, float staminaCostTick) {
            this(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, Optional.empty(), Optional.empty(), resumeSoundUserId, userId, false, false, staminaCostTick, 0);
        }

        public Instance(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, int userId, String visualRoute, int resumeSoundUserId, int resumeVoiceLineUserId, float staminaCostTick) {
            this(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, Optional.empty(), Optional.empty(), resumeSoundUserId, resumeVoiceLineUserId, false, false, staminaCostTick, 0);
        }

        public static Instance of(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, @Nullable LivingEntity user, String visualRoute) {
            Instance instance = new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, user == null ? -1 : user.getId(), visualRoute);
            return user != null ? instance.withStandSkin(StandPower.get(user)) : instance;
        }

        public boolean covers(ChunkPos chunkPos) {
            if (chunkRange <= 0) {
                return true;
            }
            return Math.abs(chunkPos.x - centerPos.x) < chunkRange
                    && Math.abs(chunkPos.z - centerPos.z) < chunkRange;
        }

        public boolean isActive() {
            return ticksLeft > 0;
        }

        public boolean isStartupSettling() {
            return ticksPassed < 0;
        }

        public Instance withTicksLeft(int ticksLeft, boolean ticksManuallySet) {
            return withTicksLeft(ticksLeft, ticksManuallySet, false);
        }

        public Instance withTicksLeft(int ticksLeft, boolean ticksManuallySet, boolean forceResumeVoiceLine) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, this.ticksManuallySet || ticksManuallySet, this.forceResumeVoiceLine || forceResumeVoiceLine, staminaCostTick, Math.max(ticksPassed, 0), refundUnusedStartCost);
        }

        public Instance withTiming(int ticksLeft, int totalTicks) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed, refundUnusedStartCost);
        }

        public Instance withArea(ChunkPos centerPos, int chunkRange) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed, refundUnusedStartCost);
        }

        public Instance withVisualRoute(String visualRoute) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed, refundUnusedStartCost);
        }

        public Instance withStaminaCostTick(float staminaCostTick) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed, refundUnusedStartCost);
        }

        public Instance withResumeSoundUserId(int resumeSoundUserId) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed, refundUnusedStartCost);
        }

        public Instance withResumeSoundAndVoiceLineUserIds(int resumeSoundUserId, int resumeVoiceLineUserId) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed, refundUnusedStartCost);
        }

        public Instance withForceResumeVoiceLine(boolean forceResumeVoiceLine) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed, refundUnusedStartCost);
        }

        public Instance withRefundUnusedStartCost(boolean refundUnusedStartCost) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed, refundUnusedStartCost);
        }

        public Instance withStartupDelay(int startupDelayTicks) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, -Math.max(startupDelayTicks, 0), refundUnusedStartCost);
        }

        public Instance withStandSkin(@Nullable StandPower power) {
            if (power == null || !power.hasPower()) {
                return this;
            }
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute,
                    power.getStandInstance().map(stand -> stand.getStandId()),
                    power.getSelectedSkin(),
                    resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed, refundUnusedStartCost);
        }

        public Instance tickDown() {
            if (ticksPassed < 0) {
                return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed + 1, refundUnusedStartCost);
            }
            return new Instance(id, ticksLeft - 1, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed + 1, refundUnusedStartCost);
        }
    }
}
