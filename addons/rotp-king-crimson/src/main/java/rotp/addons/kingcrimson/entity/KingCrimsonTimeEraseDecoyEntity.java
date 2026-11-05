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

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import rotp.core.powersystem.standpower.StandUtil;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
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

    @Nullable
    private LivingEntity owner;
    // 1.16 KCConfig timeEraseDuration; the client takes the server's value from the spawn data
    private int lifespanTicks =
            KingCrimsonConfig.timeEraseDurationTicks();
    // Not tickCount: vanilla advances that on ticks a slow-down (Made in Heaven's mode A) cancels, while the
    // eraser's own erase is slowed by the same rate; the decoy ages only on the ticks it runs.
    private int livedTicks;
    private boolean clientStandPresentationPending = true;
    private boolean removingStand;
    // Kept apart from the power attachment: a level change or a rewind reload replaces the attachment
    @Nullable
    private StandEntity visualStand;
    private boolean clientStandHidden;

    public KingCrimsonTimeEraseDecoyEntity(
            EntityType<? extends KingCrimsonTimeEraseDecoyEntity> type,
            Level level) {
        super(type, level);
        PowerClass.STAND.attachGet(this);
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
        copyOwnerPresentation(newOwner);

        StandPower sourcePower = StandPower.get(newOwner);
        StandPower decoyPower = PowerClass.STAND.attachGet(this);
        if (sourcePower != null) {
            decoyPower.setStandInstance(
                    sourcePower.getStandInstance());
        }
    }

    @Nullable
    public LivingEntity getOwner() {
        resolveOwner();
        return owner;
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
        if (isOwnProjection(target, viewer) || isOutOfStoppedTime(target)) {
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

    @Override
    protected void registerGoals() {
    }

    @Override
    protected void customServerAiStep() {
    }

    @Override
    public boolean shouldShowName() {
        return false;
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
