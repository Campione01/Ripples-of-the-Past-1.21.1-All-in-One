package rotp.addons.kingcrimson.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.adventure.npc.PowerUserMobEntity;
import rotp.core.api.perception.PerceptionBlockers;
import rotp.core.network.s2c.TrSetStandEntityPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.effect.KingCrimsonTimeEraseEffect;
import rotp.addons.kingcrimson.state.KingCrimsonCreatureErase;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.addons.kingcrimson.state.KingCrimsonShiftWalk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import rotp.core.powersystem.standpower.StandUtil;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.network.PacketDistributor;

public final class KingCrimsonTimeEraseDecoyEntity
        extends PowerUserMobEntity
        implements IEntityWithComplexSpawn {
    private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
            SynchedEntityData.defineId(
                    KingCrimsonTimeEraseDecoyEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>>
            DATA_OWNER_UUID = SynchedEntityData.defineId(
                    KingCrimsonTimeEraseDecoyEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_SKIN_PARTS =
            SynchedEntityData.defineId(
                    KingCrimsonTimeEraseDecoyEntity.class,
                    EntityDataSerializers.INT);
    // Beyond this a player's last step was a teleport, not a motion to carry on.
    private static final double MAX_KEPT_STEP = 2.0D;
    private static final double LEDGE_LOOK_AHEAD = 0.1D;
    private static final double STEP_LOOK_AHEAD = 0.5D;
    private static final double STEP_CLEARANCE = 1.2D;
    private static final double UNSAFE_LOOK_AHEAD = 0.7D;

    @Nullable
    private LivingEntity owner;
    // Its erase's length, given at the spawn; the client takes the server's value from the spawn data.
    private int lifespanTicks;
    // Not tickCount: vanilla advances that on ticks a slow-down (Made in Heaven's mode A) cancels, and the decoy
    // ages only on the ticks it runs. Its erase ends it first.
    private int livedTicks;
    private boolean clientStandPresentationPending = true;
    private boolean removingStand;
    // Kept apart from the power attachment: a level change or a rewind reload replaces the attachment
    @Nullable
    private StandEntity visualStand;
    private boolean clientStandHidden;
    // Owner's choice of 2026-11-05, after Roundabout's King Crimson clone: what the user's keys did when he erased
    // time, which the decoy goes on doing. Server only and never saved.
    private float keptForward;
    private float keptLeft;
    private boolean keptJumping;
    // Owner 2026-11-07: what it does beyond those keys, kiting or fleeing and never striking. Server only.
    private final KingCrimsonDecoyMind mind = new KingCrimsonDecoyMind(this);

    public KingCrimsonTimeEraseDecoyEntity(
            EntityType<? extends KingCrimsonTimeEraseDecoyEntity> type,
            Level level) {
        super(type, level);
        PowerClass.STAND.attachGet(this);
        moveControl = new KeptKeysMoveControl();
        lookControl = new KeptLookControl();
        if (getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
            ground.setCanFloat(true);
        }
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER_ID, -1);
        builder.define(DATA_OWNER_UUID, Optional.empty());
        builder.define(DATA_SKIN_PARTS, 0);
    }

    public void setOwner(ServerPlayer newOwner) {
        owner = newOwner;
        entityData.set(DATA_OWNER_ID, newOwner.getId());
        entityData.set(
                DATA_OWNER_UUID,
                Optional.of(newOwner.getUUID()));
        entityData.set(
                DATA_PROFILE,
                Optional.of(new ResolvableProfile(
                        newOwner.getGameProfile())));

        int skinParts = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (newOwner.isModelPartShown(part)) {
                skinParts |= part.getMask();
            }
        }
        entityData.set(DATA_SKIN_PARTS, skinParts);
        copyOwnerSpeed(newOwner);
        copyOwnerPresentation(newOwner);
        keepOwnerKeys(newOwner);
        mind.chooseAtThePress(newOwner);

        StandPower sourcePower = StandPower.get(newOwner);
        StandPower decoyPower = PowerClass.STAND.attachGet(this);
        if (sourcePower != null) {
            decoyPower.setStandInstance(
                    sourcePower.getStandInstance());
        }
    }

    public void setLifespanTicks(int ticks) {
        lifespanTicks = Math.max(ticks, 0);
    }

    @Nullable
    public LivingEntity getOwner() {
        resolveOwner();
        return owner;
    }

    /** What it does by itself, and of whom it is wary. */
    public KingCrimsonDecoyMind mind() {
        return mind;
    }

    @Override
    public boolean isPickable() {
        return !(getOwner() instanceof Player player && player.isLocalPlayer())
                && !isOwnerErasePaused()
                && super.isPickable();
    }

    // T1: while the owner's erase is frozen in a time stop the projection is out of time, like its owner.
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return isOwnerErasePaused()
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || super.isInvulnerableTo(source);
    }

    public boolean isOwnerErasePaused() {
        return getOwner() instanceof Player player
                && KingCrimsonTemporalStateService.isErasePaused(player);
    }

    /**
     * An erasing user frozen in someone's time stop, his projection, or a Stand of either: none of them can be
     * targeted until time resumes (owner rule T1).
     */
    public static boolean isOutOfStoppedTime(@Nullable Entity target) {
        Entity body = target instanceof StandEntity stand ? stand.getUser() : target;
        if (body instanceof KingCrimsonTimeEraseDecoyEntity decoy) {
            return decoy.isOwnerErasePaused();
        }
        return body instanceof Player player
                && KingCrimsonTemporalStateService.isErasePaused(player);
    }

    public static boolean isOwnProjection(Entity target, @Nullable Entity aiming) {
        LivingEntity user = aiming instanceof LivingEntity living ? StandUtil.getStandUser(living) : null;
        Entity projected = target instanceof StandEntity stand ? stand.getUser() : target;
        return user != null && projected instanceof KingCrimsonTimeEraseDecoyEntity decoy
                && decoy.getOwner() == user;
    }

    /**
     * King Crimson's rule in the core's PerceptionBlockers, on both sides, for every sight: the caster does not take
     * his own projection; nobody but the eraser himself perceives an erasing user or his Stand (T1, T3-02, T3-13,
     * T3-19); a paused eraser's projection and its Stand are out of time too.
     */
    public static boolean blocksPerception(
            @Nullable Entity viewer, Entity target, PerceptionBlockers.Sight sight) {
        // What the erase of a creature keeps in the world for itself is there for nobody's aim.
        if (isOwnProjection(target, viewer) || isOutOfStoppedTime(target) || KingCrimsonCreatureErase.isPart(target)) {
            return true;
        }
        Entity body = target instanceof StandEntity stand ? stand.getUser() : target;
        return body instanceof Player eraser
                && KingCrimsonTemporalStateService.isErasing(eraser)
                && viewingBody(viewer) != eraser;
    }

    @Nullable
    private static Entity viewingBody(@Nullable Entity viewer) {
        if (viewer instanceof LivingEntity living) {
            return StandUtil.getStandUser(living);
        }
        return viewer instanceof Projectile projectile ? projectile.getOwner() : viewer;
    }

    /** Client only: records whether this client hid the decoy's Stand; true when that changed. */
    public boolean markClientStandHidden(boolean hidden) {
        if (clientStandHidden == hidden) {
            return false;
        }
        clientStandHidden = hidden;
        return true;
    }

    public boolean isSkinPartShown(PlayerModelPart part) {
        return (entityData.get(DATA_SKIN_PARTS)
                & part.getMask()) != 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            fixClientStandPresentation();
            return;
        }

        if (isOwnerErasePaused()) {
            // a decoy outside the stopped area still ticks; its erase does not
            lifespanTicks++;
        }
        livedTicks++;
        if (!isValidDecoy() || livedTicks > lifespanTicks) {
            discard();
            return;
        }
        if (livedTicks == 1) {
            summonStandSilently();
        }
    }

    // Its mind does the rest (customServerAiStep): these two keep it afloat and let it through the doors on its path.
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
    }

    @Override
    protected void customServerAiStep() {
        mind.think();
    }

    // A player's name stands over his head, and so over his decoy's: the swap must not take it away.
    @Override
    public boolean shouldShowName() {
        return true;
    }

    @Override
    protected Component getTypeName() {
        return entityData.get(DATA_PROFILE)
                .flatMap(ResolvableProfile::name)
                .<Component>map(Component::literal)
                .orElseGet(super::getTypeName);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && isAlive()) {
            mind.onHit(source.getEntity());
        }
        return hurt;
    }

    // Owner 2026-11-07: "我們的ai不打傷害". Nothing of the decoy strikes; KingCrimsonDecoyRules stops what else would.
    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    // It holds copies of what its user holds and wears: nothing of it is left in the world ("直接消失").
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
    }

    // A player's air control, so a kept sprint jump carries as far as his (Mob's is 0.02 whatever it does).
    @Override
    protected float getFlyingSpeed() {
        return isSprinting() ? 0.026F : 0.02F;
    }

    // Two bodies are not there for the decoy: its erased user, whom nothing should give away, and the core's wrapper
    // player, which ticks one step behind the mob and would push it along by about 16%.
    @Override
    public void push(Entity other) {
        if (!isOwnBody(other)) {
            super.push(other);
        }
    }

    @Override
    protected void doPush(Entity other) {
        if (!isOwnBody(other)) {
            super.doPush(other);
        }
    }

    private boolean isOwnBody(Entity other) {
        return other == getOwner() || other == playerWrapper;
    }

    // Owner's choice of 2026-11-05: a killed decoy simply disappears, with no body falling over.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) {
            discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && !removingStand) {
            removeSummonedStand();
        }
        super.remove(reason);
    }

    // A cross-world teleport only setRemoved()s the old entity and never reaches remove(RemovalReason)
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide() && !removingStand) {
            removeSummonedStand();
        }
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityData.get(DATA_OWNER_ID));
        Optional<UUID> ownerUuid =
                entityData.get(DATA_OWNER_UUID);
        buffer.writeBoolean(ownerUuid.isPresent());
        ownerUuid.ifPresent(buffer::writeUUID);
        buffer.writeVarInt(lifespanTicks);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        entityData.set(DATA_OWNER_ID, buffer.readVarInt());
        entityData.set(
                DATA_OWNER_UUID,
                buffer.readBoolean()
                        ? Optional.of(buffer.readUUID())
                        : Optional.empty());
        lifespanTicks = buffer.readVarInt();
        resolveOwner();
    }

    // The user's speed as it was before the erase: the erase's own boost is his alone.
    private void copyOwnerSpeed(LivingEntity source) {
        AttributeInstance from = source.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance to = getAttribute(Attributes.MOVEMENT_SPEED);
        if (from == null || to == null) {
            return;
        }
        to.setBaseValue(from.getBaseValue());
        for (AttributeModifier modifier : from.getModifiers()) {
            if (!modifier.id().equals(KingCrimsonTimeEraseEffect.MOVEMENT_SPEED_ID)) {
                to.addOrReplacePermanentModifier(modifier);
            }
        }
    }

    private void keepOwnerKeys(LivingEntity source) {
        // On a mount his keys steer the mount; the decoy stays out of it.
        if (source.isPassenger()) {
            return;
        }
        PlayerMovementInputData keys = PlayerMovementInputData.get(source);
        if (keys != null) {
            keptForward = keys.forward;
            keptLeft = keys.left;
            keptJumping = keys.jumping;
        }
        if (source instanceof Player player) {
            Vec3 step = KingCrimsonAddon.temporalState(player).lastStepTo(source.position());
            if (step.lengthSqr() <= MAX_KEPT_STEP * MAX_KEPT_STEP) {
                setDeltaMovement(step);
            }
        }
    }

    private void actOutKeptKeys() {
        float forward = 0.0F;
        float left = 0.0F;
        boolean jumping = false;
        boolean ownWay = false;
        // A decoy outside the stopped area still ticks, but its erase is frozen: it waits for its owner.
        if (!isOwnerErasePaused()) {
            if (mind.usesKeptKeys()) {
                forward = keptForward;
                left = keptLeft;
                jumping = keptJumping;
            } else {
                // Its mind's way, on a player's keys: it looks where the mind says and presses what takes it there.
                ownWay = true;
                if (mind.mode() == KingCrimsonDecoyMind.Mode.FLEE) {
                    setShiftKeyDown(false);
                }
                if (mind.turnsView()) {
                    turnView(mind.viewYaw(), mind.viewPitch(), mind.turnRate());
                }
                Vec3 travel = mind.travel();
                if (travel != Vec3.ZERO) {
                    float sin = Mth.sin(getYRot() * Mth.DEG_TO_RAD);
                    float cos = Mth.cos(getYRot() * Mth.DEG_TO_RAD);
                    forward = (float) (travel.z * cos - travel.x * sin);
                    left = (float) (travel.x * cos + travel.z * sin);
                }
                jumping = mind.jumps();
                setSprinting(mind.sprints() && forward > 0.8F);
            }
        }
        Vec3 heading = headingOf(forward, left);
        boolean sneaking = isShiftKeyDown();
        if (heading != Vec3.ZERO && onGround()
                && (sneaking && isLedgeAhead(heading) || ownWay && isUnsafeAhead(heading))) {
            jumping = jumping && !ownWay;
            forward = 0.0F;
            left = 0.0F;
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        }
        // Mob.setSpeed also sets the forward input.
        setSpeed((float) getAttributeValue(Attributes.MOVEMENT_SPEED));
        setZza(forward);
        setXxa(left);
        setPose(isSwimming() ? Pose.SWIMMING : sneaking ? Pose.CROUCHING : Pose.STANDING);

        // A player's hands would hop a one-block step, just before it so as to keep his speed; a wall too high ends
        // the hopping, even with the key held.
        boolean blocked = heading != Vec3.ZERO && isBlockedAhead(heading);
        boolean hops = jumping || blocked && !sneaking;
        if (hops && (!blocked || level().noCollision(this, getBoundingBox().move(
                heading.x * STEP_LOOK_AHEAD, STEP_CLEARANCE, heading.z * STEP_LOOK_AHEAD)))) {
            getJumpControl().jump();
        }
    }

    private void turnView(float yaw, float pitch, float rate) {
        float turned = Mth.approachDegrees(getYRot(), yaw, rate);
        setYRot(turned);
        setYHeadRot(turned);
        setYBodyRot(turned);
        setXRot(Mth.approach(getXRot(), Mth.clamp(pitch, -60.0F, 60.0F), rate * 0.5F));
    }

    float keptForward() {
        return keptForward;
    }

    float keptLeft() {
        return keptLeft;
    }

    /** Where the kept keys take it, as it looks now; Vec3.ZERO with no key held. */
    Vec3 keptHeading() {
        return headingOf(keptForward, keptLeft);
    }

    /** A wall this way that a hop will not take. */
    boolean isStoppedAhead(Vec3 heading) {
        return isBlockedAhead(heading) && !level().noCollision(this, getBoundingBox().move(
                heading.x * STEP_LOOK_AHEAD, STEP_CLEARANCE, heading.z * STEP_LOOK_AHEAD));
    }

    /** A drop of more than three blocks, or a hazard, within a step this way. Water is neither: it swims. */
    boolean isUnsafeAhead(Vec3 heading) {
        return isUnsafeAt(heading, UNSAFE_LOOK_AHEAD);
    }

    /** Whether it can run on this way for so far: nothing to bump at, no drop and no hazard. */
    boolean isFreeAhead(Vec3 heading, double distance) {
        return level().noCollision(this, getBoundingBox().expandTowards(heading.x * distance, 0.0D, heading.z * distance))
                && !isUnsafeAt(heading, distance * 0.5D) && !isUnsafeAt(heading, distance);
    }

    private boolean isUnsafeAt(Vec3 heading, double distance) {
        if (!(level() instanceof ServerLevel server)) {
            return false;
        }
        AABB there = getBoundingBox().move(heading.x * distance, 0.0D, heading.z * distance);
        if (KingCrimsonShiftWalk.isHazardAt(server, there)) {
            return true;
        }
        double drop = KingCrimsonShiftWalk.groundBelow(server, this, there);
        if (drop < 0.0D) {
            return !isWaterBelow(there);
        }
        return drop > 0.0D && KingCrimsonShiftWalk.isHazardAt(server, there.move(0.0D, -drop, 0.0D));
    }

    private boolean isWaterBelow(AABB box) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(box.getCenter().x, box.minY, box.getCenter().z).mutable();
        for (int depth = 0; depth <= 4; depth++) {
            if (level().getFluidState(pos).is(FluidTags.WATER)) {
                return true;
            }
            if (level().getBlockState(pos).blocksMotion()) {
                return false;
            }
            pos.move(Direction.DOWN);
        }
        return false;
    }

    // What a step's height will not carry it over.
    private boolean isBlockedAhead(Vec3 heading) {
        return !level().noCollision(this, getBoundingBox().move(
                        heading.x * STEP_LOOK_AHEAD, 0.0D, heading.z * STEP_LOOK_AHEAD))
                && !level().noCollision(this, getBoundingBox().move(
                        heading.x * STEP_LOOK_AHEAD, maxUpStep(), heading.z * STEP_LOOK_AHEAD));
    }

    // As Entity.getInputVector turns a player's keys into a direction.
    private Vec3 headingOf(float forward, float left) {
        if (forward == 0.0F && left == 0.0F) {
            return Vec3.ZERO;
        }
        float sin = Mth.sin(getYRot() * Mth.DEG_TO_RAD);
        float cos = Mth.cos(getYRot() * Mth.DEG_TO_RAD);
        return new Vec3(left * cos - forward * sin, 0.0D, forward * cos + left * sin).normalize();
    }

    // As Player.maybeBackOffFromEdge sees a ledge.
    private boolean isLedgeAhead(Vec3 heading) {
        return level().noCollision(this, getBoundingBox().move(
                heading.x * LEDGE_LOOK_AHEAD, -maxUpStep(), heading.z * LEDGE_LOOK_AHEAD));
    }

    private final class KeptKeysMoveControl extends MoveControl {
        private KeptKeysMoveControl() {
            super(KingCrimsonTimeEraseDecoyEntity.this);
        }

        @Override
        public void tick() {
            actOutKeptKeys();
        }
    }

    // A mob's look control levels its head every tick; the decoy keeps the pitch its user had.
    private final class KeptLookControl extends LookControl {
        private KeptLookControl() {
            super(KingCrimsonTimeEraseDecoyEntity.this);
        }

        @Override
        protected boolean resetXRotOnTick() {
            return false;
        }
    }

    private void copyOwnerPresentation(LivingEntity source) {
        copyPosition(source);
        setYBodyRot(source.yBodyRot);
        yBodyRotO = source.yBodyRotO;
        setYHeadRot(source.yHeadRot);
        yHeadRotO = source.yHeadRotO;
        setPose(source.getPose());
        setShiftKeyDown(source.isShiftKeyDown());
        setSprinting(source.isSprinting());
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND,
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD}) {
            setItemSlot(slot, source.getItemBySlot(slot).copy());
        }
        // The core's wrapper player writes its selected slot into the mob's main hand every tick.
        Inventory wrapperInventory = asPlayer().getInventory();
        wrapperInventory.setItem(wrapperInventory.selected, getMainHandItem());
    }

    private boolean isValidDecoy() {
        LivingEntity currentOwner = getOwner();
        return currentOwner instanceof ServerPlayer player
                && currentOwner.isAlive()
                && currentOwner.level() == level()
                && KingCrimsonAddon.temporalState(player)
                        .isErasing(level().getGameTime());
    }

    private void summonStandSilently() {
        StandPower decoyPower = PowerClass.STAND.attachGet(this);
        if (!(decoyPower.getPowerType()
                instanceof EntityStandType standType)
                || decoyPower.isSummoned()) {
            return;
        }
        Entity created = standType.getEntityType().create(level());
        if (!(created instanceof StandEntity stand)) {
            return;
        }
        stand.withStandType(standType);

        LivingEntity source = this;
        LivingEntity currentOwner = getOwner();
        StandPower sourcePower = StandPower.get(currentOwner);
        if (sourcePower != null
                && sourcePower.getSummonedStandEntity() != null) {
            source = sourcePower.getSummonedStandEntity();
        }
        stand.copyPosition(source);
        stand.copyStandUserRotation(source);
        // in its summon pose a Stand's body is on its yaw from its spawn on
        // (core prepareFreshSummonState); copyStandUserRotation leaves it at 0
        stand.yBodyRot = stand.getYRot();
        stand.yBodyRotO = stand.yBodyRot;
        stand.summonLockTicks = 0;
        stand.gradualSummonWeaknessTicks = 0;
        // The decoy explicitly owns this visual Stand's lifetime.
        stand.healthLinkedWithUser = false;
        decoyPower.setSummonedStand(stand);

        if (!level().addFreshEntity(stand)) {
            decoyPower.setSummonedStand(null);
            stand.discard();
            return;
        }
        visualStand = stand;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                this,
                new TrSetStandEntityPacket(
                        getId(), stand.getId()));
    }

    private void fixClientStandPresentation() {
        if (!clientStandPresentationPending) {
            return;
        }
        StandPower power = StandPower.get(this);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        if (stand == null) {
            return;
        }
        stand.tickCount = Math.max(stand.tickCount, 999);
        stand.summonLockTicks = 0;
        stand.gradualSummonWeaknessTicks = 0;
        stand.overlayTickCount = Math.max(
                stand.overlayTickCount, 999);
        clientStandPresentationPending = false;
    }

    private void removeSummonedStand() {
        removingStand = true;
        try {
            StandPower power = StandPower.get(this);
            StandEntity stand = power != null
                    ? power.getSummonedStandEntity()
                    : null;
            if (power != null) {
                power.setSummonedStand(null);
            }
            if (stand != null) {
                stand.remove(RemovalReason.DISCARDED);
            }
            if (visualStand != null) {
                if (!visualStand.isRemoved()) {
                    visualStand.remove(RemovalReason.DISCARDED);
                }
                visualStand = null;
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    this,
                    new TrSetStandEntityPacket(getId(), 0));
        } finally {
            removingStand = false;
        }
    }

    private void resolveOwner() {
        Optional<UUID> expectedUuid =
                entityData.get(DATA_OWNER_UUID);
        if (owner != null
                && !owner.isRemoved()
                && owner.level() == level()
                && owner.getId() == entityData.get(DATA_OWNER_ID)
                && expectedUuid
                        .filter(owner.getUUID()::equals)
                        .isPresent()) {
            return;
        }
        owner = null;
        Entity byId = level().getEntity(
                entityData.get(DATA_OWNER_ID));
        if (byId instanceof LivingEntity living
                && expectedUuid
                        .filter(living.getUUID()::equals)
                        .isPresent()) {
            owner = living;
            return;
        }
        if (level() instanceof ServerLevel serverLevel
                && expectedUuid.isPresent()) {
            Entity byUuid = serverLevel.getEntity(
                    expectedUuid.get());
            if (byUuid instanceof LivingEntity living) {
                owner = living;
            }
        }
    }
}
