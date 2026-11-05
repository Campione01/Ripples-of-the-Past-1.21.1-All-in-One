package rotp.addons.darkbluemoon.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.JojoModUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 BarnaclePullEntity: a barnacle line the Stand shoots along its look at 1.5 blocks a tick; it never stops on its own
 * (1.16 emptied updateMotionFlags), only a block makes it retract, and 40 ticks without a catch end it. A living entity it
 * meets that is not blocking is caught and dragged to the Stand at 0.8 a tick under Slowness V until within 2 blocks.
 */
public class BarnaclePullEntity extends OwnerBoundProjectileEntity {
    private static final EntityDataAccessor<Integer> ATTACHED_ENTITY =
            SynchedEntityData.defineId(BarnaclePullEntity.class, EntityDataSerializers.INT);
    private static final Vec3 OFFSET = new Vec3(0.0, -0.2, 0.0);
    private static final int LIFESPAN = 40;
    private static final float MOVEMENT_SPEED = 1.5F;

    @Nullable
    private UUID userUUID;
    @Nullable
    private UUID attachedEntityUUID;
    private double distance;
    private boolean retracting;
    private final Set<Entity> dragged = new HashSet<>();

    public BarnaclePullEntity(Level level, StandEntity stand) {
        super(DarkBlueMoonAddon.BARNACLE_PULL_ENTITY.get(), stand, level);
        LivingEntity user = stand.getUser();
        this.userUUID = user != null ? user.getUUID() : null;
    }

    public BarnaclePullEntity(EntityType<? extends BarnaclePullEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACHED_ENTITY, -1);
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    @Override
    protected float getBaseDamage() {
        return 2.0F;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return 0.0F;
    }

    @Override
    public int ticksLifespan() {
        return getEntityAttachedTo() == null ? LIFESPAN : Integer.MAX_VALUE;
    }

    @Override
    public boolean isBodyPart() {
        return true;
    }

    @Override
    protected Vec3 getOwnerRelativeOffset() {
        return OFFSET;
    }

    @Override
    protected boolean shouldFollowOwner() {
        return false;
    }

    @Override
    public void tick() {
        if (!level().isClientSide() && attachedEntityUUID != null && entityData.get(ATTACHED_ENTITY) < 0
                && level() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(attachedEntityUUID) instanceof LivingEntity living) {
            attachToEntity(living);
        }
        dragged.forEach(entity -> entity.setDeltaMovement(Vec3.ZERO));
        dragged.clear();
        super.tick();
        if (!isAlive()) {
            return;
        }
        LivingEntity bound = getEntityAttachedTo();
        if (bound == null || level().isClientSide()) {
            return;
        }
        LivingEntity owner = getOwner();
        if (owner == null) {
            discard();
            return;
        }
        if (!bound.isAlive() || owner.distanceToSqr(bound) > 100.0) {
            discard();
            return;
        }
        Vec3 vecToOwner = owner.position().subtract(bound.position());
        if (vecToOwner.length() < 2.0) {
            discard();
            return;
        }
        dragTarget(bound, vecToOwner.normalize().scale(0.8));
        bound.fallDistance = 0.0F;
        bound.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4, false, false, false));
    }

    // 1.16 OwnerBoundProjectileEntity.dragTarget: the motion is set for one tick and taken back in the next
    private void dragTarget(Entity entity, Vec3 vec) {
        Entity dragged = entity.getRootVehicle();
        dragged.setDeltaMovement(vec);
        dragged.hurtMarked = true;
        this.dragged.add(dragged);
    }

    @Override
    protected void moveProjectile() {
        LivingEntity bound = getEntityAttachedTo();
        if (bound != null) {
            moveTo(bound.getX(), bound.getY(0.5), bound.getZ(), bound.getYRot(), bound.getXRot());
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        LivingEntity owner = getOwner();
        if (owner == null) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        setRot(owner.getYRot(), owner.getXRot());
        double nextDistance = retracting
                ? distance - MOVEMENT_SPEED * speedFactor
                : distance + MOVEMENT_SPEED * speedFactor;
        if (retracting && nextDistance <= 0) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        distance = nextDistance;
        Vec3 origin = ownerPosition(1.0F, false);
        Vec3 next = origin.add(Vec3.directionFromRotation(owner.getXRot(), owner.getYRot()).scale(distance));
        double x = getX();
        double y = getY();
        double z = getZ();
        setDeltaMovement(next.x - x, next.y - y, next.z - z);
        xo = x;
        yo = y;
        zo = z;
        xOld = x;
        yOld = y;
        zOld = z;
        setPos(next.x, next.y, next.z);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        LivingEntity owner = getOwner();
        if (entity.is(owner) || !(entity instanceof LivingEntity)) {
            return false;
        }
        if (!(owner instanceof StandEntity stand)) {
            return true;
        }
        return !entity.is(stand.getUser()) || !stand.isFollowingUser();
    }

    @Override
    protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
        if (getEntityAttachedTo() == null && target instanceof LivingEntity living && !JojoModUtil.isTargetBlocking(living)) {
            attachToEntity(living);
            if (userUUID != null) {
                target.addTag(String.valueOf(userUUID));
            }
        }
        return false;
    }

    @Override
    protected void breakProjectile(TargetType targetType, HitResult hitTarget) {}

    @Override
    protected void afterBlockHit(BlockHitResult blockHitResult, boolean blockDestroyed) {
        if (!blockDestroyed) {
            retracting = true;
        }
    }

    public void attachToEntity(LivingEntity target) {
        attachedEntityUUID = null;
        entityData.set(ATTACHED_ENTITY, target.getId());
        setDeltaMovement(Vec3.ZERO);
    }

    @Nullable
    public LivingEntity getEntityAttachedTo() {
        int id = entityData.get(ATTACHED_ENTITY);
        if (id < 0) {
            return null;
        }
        return level().getEntity(id) instanceof LivingEntity living ? living : null;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        dragged.forEach(entity -> entity.setDeltaMovement(Vec3.ZERO));
        super.remove(reason);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        super.writeSpawnData(buffer);
        buffer.writeDouble(distance);
        buffer.writeBoolean(retracting);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        super.readSpawnData(additionalData);
        distance = additionalData.readDouble();
        retracting = additionalData.readBoolean();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (userUUID != null) {
            nbt.putUUID("UserServerId", userUUID);
        }
        LivingEntity attached = getEntityAttachedTo();
        if (attached != null) {
            nbt.putUUID("AttachedEntity", attached.getUUID());
        }
        nbt.putDouble("Distance", distance);
        nbt.putBoolean("Retracting", retracting);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.hasUUID("UserServerId")) {
            userUUID = nbt.getUUID("UserServerId");
        }
        attachedEntityUUID = nbt.hasUUID("AttachedEntity") ? nbt.getUUID("AttachedEntity") : null;
        distance = nbt.getDouble("Distance");
        retracting = nbt.getBoolean("Retracting");
    }
}
