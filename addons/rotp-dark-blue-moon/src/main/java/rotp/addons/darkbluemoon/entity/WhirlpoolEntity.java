package rotp.addons.darkbluemoon.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.DarkBlueMoonConfig;
import rotp.addons.darkbluemoon.util.DbmWater;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 DarkBlueMoonWhirlpoolEntity: after its charge it pulls every entity within its radius to its centre and hurts the
 * living ones with magic damage, more near the centre. Dark Blue Moon users with their Stand out and its owner are spared.
 */
public class WhirlpoolEntity extends Entity {
    private static final EntityDataAccessor<Integer> WATER_COUNT =
            SynchedEntityData.defineId(WhirlpoolEntity.class, EntityDataSerializers.INT);
    @Nullable
    private UUID ownerUUID;
    private int ticks;

    public WhirlpoolEntity(EntityType<? extends WhirlpoolEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public WhirlpoolEntity(Level level, LivingEntity owner, Vec3 center) {
        this(DarkBlueMoonAddon.WHIRLPOOL.get(), level);
        ownerUUID = owner.getUUID();
        setPos(center.x, center.y, center.z);
        if (!level.isClientSide()) {
            setWaterCount(countWater(level, blockPosition()));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WATER_COUNT, 0);
    }

    /** The whirlpool's own age, counted on each side as in 1.16; the renderer turns it by this. */
    public int getTickCount() {
        return ticks;
    }

    public int getWaterCount() {
        return entityData.get(WATER_COUNT);
    }

    public void setWaterCount(int count) {
        entityData.set(WATER_COUNT, count);
    }

    @Nullable
    public LivingEntity getOwner() {
        if (ownerUUID == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(ownerUUID) instanceof LivingEntity living ? living : null;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (level().isClientSide()) {
            return;
        }
        int chargeTime = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.WHIRLPOOL_CHARGE_TIME);
        int lifespan = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.WHIRLPOOL_LIFESPAN);
        double radius = DarkBlueMoonConfig.getDouble(DarkBlueMoonConfig.WHIRLPOOL_RADIUS);
        double pullSpeed = DarkBlueMoonConfig.getDouble(DarkBlueMoonConfig.WHIRLPOOL_PULL_SPEED);
        double minDamage = DarkBlueMoonConfig.getDouble(DarkBlueMoonConfig.WHIRLPOOL_MIN_DAMAGE);
        double maxDamage = DarkBlueMoonConfig.getDouble(DarkBlueMoonConfig.WHIRLPOOL_MAX_DAMAGE);
        if (ticks >= lifespan) {
            discard();
            return;
        }
        if (ticks % 20 == 0) {
            setWaterCount(countWater(level(), blockPosition()));
        }
        if (ticks >= chargeTime && ticks % 20 == 0) {
            playSound(DbmWater.randomWaterUseSound(random), 1.0F, 1.0F);
        }
        if (ticks < chargeTime) {
            return;
        }
        LivingEntity owner = getOwner();
        if (owner == null) {
            discard();
            return;
        }
        for (Entity entity : level().getEntitiesOfClass(Entity.class, getBoundingBox().inflate(radius),
                e -> e != owner && e.isAlive())) {
            if (entity == this || isImmune(entity)) {
                continue;
            }
            Vec3 toCenter = position().subtract(entity.position());
            double dist = toCenter.length();
            if (dist < 0.5) {
                continue;
            }
            entity.setDeltaMovement(entity.getDeltaMovement().add(toCenter.normalize().scale(pullSpeed)));
            entity.hurtMarked = true;
            if (entity instanceof LivingEntity living) {
                double ratio = 1.0 - dist / radius;
                float damage = (float) (minDamage + ratio * (maxDamage - minDamage));
                living.hurt(damageSources().magic(), damage);
            }
        }
    }

    private static int countWater(Level level, BlockPos center) {
        return DbmWater.countWater(level, center,
                DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.WHIRLPOOL_WATER_CHECK_RADIUS), -1);
    }

    private static boolean isImmune(Entity entity) {
        if (entity instanceof Player player) {
            StandPower power = StandPower.get(player);
            return power != null && power.hasPower() && power.getSummonedStandEntity() instanceof DarkBlueMoonEntity;
        }
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.hasUUID("OwnerUUID")) {
            ownerUUID = nbt.getUUID("OwnerUUID");
        }
        ticks = nbt.getInt("TickCount");
        entityData.set(WATER_COUNT, nbt.getInt("WaterCount"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        if (ownerUUID != null) {
            nbt.putUUID("OwnerUUID", ownerUUID);
        }
        nbt.putInt("TickCount", ticks);
        nbt.putInt("WaterCount", getWaterCount());
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
