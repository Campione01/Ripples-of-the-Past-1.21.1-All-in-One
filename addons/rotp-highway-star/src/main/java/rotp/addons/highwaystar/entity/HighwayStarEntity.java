package rotp.addons.highwaystar.entity;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.HighwayStarConfig;
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

    private boolean pendingSplit = false;
    private int splitSwitchTicks = 0;
    @Nullable private UUID trackingTarget = null;
    @Nullable private UUID remoteSoleUuid = null;

    public HighwayStarEntity(EntityType<? extends HighwayStarEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_SKATING, false);
        builder.define(IS_SPLIT, false);
        builder.define(ARM_MISSING, false);
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
        return pendingSplit;
    }

    public void setPendingSplit(boolean pendingSplit) {
        this.pendingSplit = pendingSplit;
    }

    public int getSplitSwitchTicks() {
        return splitSwitchTicks;
    }

    public void setSplitSwitchTicks(int ticks) {
        this.splitSwitchTicks = ticks;
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
            return;
        }
        if (splitSwitchTicks > 0) {
            splitSwitchTicks--;
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

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()) {
            LivingEntity user = getUser();
            if (user != null) {
                for (HighwayStarSoleEntity sole : ownedSoles(level(), user, 128.0)) {
                    sole.discard();
                }
            }
        }
        super.remove(reason);
    }
}
