package rotp.addons.darkbluemoon.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 1.16 BarnacleEntity: a barnacle stuck on a bound entity at a fixed offset. */
public class BarnacleEntity extends Entity {
    private static final EntityDataAccessor<Integer> TARGET_ID =
            SynchedEntityData.defineId(BarnacleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> OFFSET_X =
            SynchedEntityData.defineId(BarnacleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Y =
            SynchedEntityData.defineId(BarnacleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Z =
            SynchedEntityData.defineId(BarnacleEntity.class, EntityDataSerializers.FLOAT);
    @Nullable
    private UUID targetUUID;

    public BarnacleEntity(EntityType<? extends BarnacleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public BarnacleEntity(Level level, LivingEntity target, Vec3 localOffset) {
        this(DarkBlueMoonAddon.BARNACLE_ENTITY.get(), level);
        targetUUID = target.getUUID();
        entityData.set(TARGET_ID, target.getId());
        setOffset(localOffset);
        updatePositionFromTarget(target);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET_ID, -1);
        builder.define(OFFSET_X, 0.0F);
        builder.define(OFFSET_Y, 0.0F);
        builder.define(OFFSET_Z, 0.0F);
    }

    public void setOffset(Vec3 offset) {
        entityData.set(OFFSET_X, (float) offset.x);
        entityData.set(OFFSET_Y, (float) offset.y);
        entityData.set(OFFSET_Z, (float) offset.z);
    }

    public Vec3 getOffset() {
        return new Vec3(entityData.get(OFFSET_X), entityData.get(OFFSET_Y), entityData.get(OFFSET_Z));
    }

    @Nullable
    public LivingEntity getTarget() {
        if (level() instanceof ServerLevel serverLevel) {
            if (targetUUID == null) {
                return null;
            }
            return serverLevel.getEntity(targetUUID) instanceof LivingEntity living ? living : null;
        }
        return level().getEntity(entityData.get(TARGET_ID)) instanceof LivingEntity living ? living : null;
    }

    private void updatePositionFromTarget(LivingEntity target) {
        Vec3 offset = getOffset();
        setPos(target.getX() + offset.x, target.getY() + offset.y, target.getZ() + offset.z);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            updatePositionFromTarget(target);
        }
        else if (!level().isClientSide()) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.hasUUID("TargetUUID")) {
            targetUUID = nbt.getUUID("TargetUUID");
        }
        entityData.set(OFFSET_X, nbt.getFloat("OffX"));
        entityData.set(OFFSET_Y, nbt.getFloat("OffY"));
        entityData.set(OFFSET_Z, nbt.getFloat("OffZ"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        if (targetUUID != null) {
            nbt.putUUID("TargetUUID", targetUUID);
        }
        Vec3 offset = getOffset();
        nbt.putFloat("OffX", (float) offset.x);
        nbt.putFloat("OffY", (float) offset.y);
        nbt.putFloat("OffZ", (float) offset.z);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
