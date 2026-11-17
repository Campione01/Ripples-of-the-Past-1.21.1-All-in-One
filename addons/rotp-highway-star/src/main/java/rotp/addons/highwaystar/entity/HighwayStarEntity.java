package rotp.addons.highwaystar.entity;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.HighwayStarConfig;
import rotp.addons.highwaystar.ability.HighwayStarUtilityAbility;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HighwayStarEntity extends StandEntity {
    private static final EntityDataAccessor<Boolean> IS_SKATING =
            SynchedEntityData.defineId(HighwayStarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_SPLIT =
            SynchedEntityData.defineId(HighwayStarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ARM_MISSING =
            SynchedEntityData.defineId(HighwayStarEntity.class, EntityDataSerializers.BOOLEAN);
    // 1.16 kept these two on the server, so its HighwayStarModel never saw the split coming and its shrink never drew.
    private static final EntityDataAccessor<Boolean> PENDING_SPLIT =
            SynchedEntityData.defineId(HighwayStarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SPLIT_SWITCH_TICKS =
            SynchedEntityData.defineId(HighwayStarEntity.class, EntityDataSerializers.INT);
    // The division clip's squash is over 0.2917 s in, about 6 of the 10 ticks before the split.
    public static final int GONE_AT_SWITCH_TICKS = 4;
    // One second covers a ping of about 500 ms; a press the server refused shows the Stand again after it.
    private static final int SQUASH_HOLD_TICKS = 20;

    @Nullable private UUID trackingTarget = null;
    @Nullable private UUID remoteSoleUuid = null;
    // Client: the squash ended here before the server's split arrived. A press runs ahead of the server, and entity data
    // sent in a tick reaches the client a tick after the soles spawned in it.
    private int squashHoldTicks = 0;

    public HighwayStarEntity(EntityType<? extends HighwayStarEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_SKATING, false);
        builder.define(IS_SPLIT, false);
        builder.define(ARM_MISSING, false);
        builder.define(PENDING_SPLIT, false);
        builder.define(SPLIT_SWITCH_TICKS, 0);
    }

    public boolean isSkating() {
        return entityData.get(IS_SKATING);
    }

    public void setSkating(boolean skating) {
        entityData.set(IS_SKATING, skating);
    }

    public boolean isSplit() {
        return entityData.get(IS_SPLIT);
    }

    public void setSplit(boolean split) {
        entityData.set(IS_SPLIT, split);
    }

    public boolean isArmMissing() {
        return entityData.get(ARM_MISSING);
    }

    public void setArmMissing(boolean missing) {
        entityData.set(ARM_MISSING, missing);
    }

    public boolean isPendingSplit() {
        return entityData.get(PENDING_SPLIT);
    }

    public void setPendingSplit(boolean pendingSplit) {
        entityData.set(PENDING_SPLIT, pendingSplit);
    }

    public int getSplitSwitchTicks() {
        return entityData.get(SPLIT_SWITCH_TICKS);
    }

    public void setSplitSwitchTicks(int ticks) {
        entityData.set(SPLIT_SWITCH_TICKS, ticks);
    }

    /**
     * Client: whether nothing of the Stand is drawn. In split form, from the end of the squash until the split reaches this
     * client, and for the one tick of the way back, whose clip would show a squashed body before the idle at full size.
     */
    public boolean isGoneFromSight() {
        if (isSplit()) {
            return true;
        }
        boolean squashing = false;
        if (getCurStandAction() instanceof HighwayStarUtilityAbility.UtilityAction action) {
            if (action.isComingBack()) {
                return true;
            }
            squashing = action.isSplitting();
        }
        return !squashing && squashHoldTicks > 0
                || isPendingSplit() && getSplitSwitchTicks() <= GONE_AT_SWITCH_TICKS;
    }

    @Nullable
    public UUID getTrackingTarget() {
        return trackingTarget;
    }

    public void setTrackingTarget(@Nullable UUID uuid) {
        this.trackingTarget = uuid;
    }

    @Nullable
    public UUID getRemoteSoleUuid() {
        return remoteSoleUuid;
    }

    public void setRemoteSoleUuid(@Nullable UUID uuid) {
        this.remoteSoleUuid = uuid;
    }

    @Nullable
    public HighwayStarSoleEntity getRemoteSole() {
        if (level() instanceof ServerLevel serverLevel && remoteSoleUuid != null) {
            Entity entity = serverLevel.getEntity(remoteSoleUuid);
            return entity instanceof HighwayStarSoleEntity sole ? sole : null;
        }
        return null;
    }

    /** The soles of the user within the radius of the user, as 1.16 found them (getEntitiesWithinAABB, box grown). */
    public static List<HighwayStarSoleEntity> ownedSoles(Level level, LivingEntity user, double radius) {
        UUID owner = user.getUUID();
        return level.getEntitiesOfClass(HighwayStarSoleEntity.class, user.getBoundingBox().inflate(radius),
                sole -> owner.equals(sole.getOwnerUUID()));
    }

    public void forceSplit() {
        if (!isSplit() && !isPendingSplit()) {
            setPendingSplit(true);
            setSplitSwitchTicks(12);
        }
    }

    public void forceReturnArm() {
        setArmMissing(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (isSplit()) {
                squashHoldTicks = 0;
            }
            else if (getCurStandAction() instanceof HighwayStarUtilityAbility.UtilityAction action && action.isSplitting()) {
                squashHoldTicks = SQUASH_HOLD_TICKS;
            }
            else if (squashHoldTicks > 0) {
                squashHoldTicks--;
            }
            return;
        }
        int splitSwitchTicks = getSplitSwitchTicks();
        if (splitSwitchTicks > 0) {
            setSplitSwitchTicks(--splitSwitchTicks);
            if (splitSwitchTicks == 1) {
                performSplitSwitch();
            }
        }

        if (trackingTarget != null) {
            if (!isSplit()) {
                return;
            }
            LivingEntity user = getUser();
            if (user == null) {
                trackingTarget = null;
                remoteSoleUuid = null;
                return;
            }
            if (!(level() instanceof ServerLevel serverLevel)) {
                return;
            }
            Entity target = serverLevel.getEntity(trackingTarget);
            if (target == null || target instanceof HighwayStarSoleEntity || !(target instanceof LivingEntity)) {
                trackingTarget = null;
                remoteSoleUuid = null;
                return;
            }
            List<HighwayStarSoleEntity> soles = ownedSoles(level(), user, 128.0);
            if (soles.isEmpty()) {
                trackingTarget = null;
                remoteSoleUuid = null;
                return;
            }
            if (level().getGameTime() % 20L == 0L) {
                int i = 0;
                for (HighwayStarSoleEntity sole : soles) {
                    sole.attachTo(trackingTarget, i++);
                }
            }
        }
    }

    public void performSplitSwitch() {
        boolean pendingSplit = isPendingSplit();
        if (isSplit() == pendingSplit) {
            return;
        }
        if (pendingSplit && isManuallyControlled()) {
            StandEntityManualControlToggle.off(level(), this, false);
        }
        setSplit(pendingSplit);
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity user = getUser();
        if (user == null) {
            return;
        }
        UUID owner = user.getUUID();
        for (HighwayStarSoleEntity sole : ownedSoles(serverLevel, user, 128.0)) {
            sole.discard();
        }
        remoteSoleUuid = null;
        if (pendingSplit) {
            Vec3 center = user.position();
            int soleCount = HighwayStarConfig.MAX_SOLES_COUNT.get();
            for (int i = 0; i < soleCount; i++) {
                HighwayStarSoleEntity sole = HighwayStarAddon.SOLE_ENTITY.get().create(serverLevel);
                if (sole != null) {
                    double angle = (Math.PI * 2) / soleCount * i;
                    double dx = Mth.cos((float) angle) * 2.0;
                    double dz = Mth.sin((float) angle) * 2.0;
                    sole.moveTo(center.x + dx, center.y, center.z + dz, 0.0F, 0.0F);
                    sole.setDeltaMovement(Vec3.ZERO);
                    sole.setOwnerUUID(owner);
                    if (i == 0) {
                        sole.setRemoteControlled(true);
                        remoteSoleUuid = sole.getUUID();
                    }
                    else {
                        sole.setRemoteControlled(false);
                    }
                    serverLevel.addFreshEntity(sole);
                    if (trackingTarget != null) {
                        sole.attachTo(trackingTarget, i);
                    }
                }
            }
        }
        else {
            trackingTarget = null;
        }
    }

}
