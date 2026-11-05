package rotp.addons.darkbluemoon.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.util.DbmWater;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 DarkBlueMoonMiniTornadoEntity: lives 100 ticks, drifts by its motion, and every tick pulls the living entities
 * within 4 blocks to its centre at 0.35, hurting them for 2 magic damage and slowing them (Slowness II, 40 ticks).
 */
public class MiniTornadoEntity extends Entity {
    public static final double RADIUS = 4.0;
    public static final int LIFESPAN = 100;
    public static final float DAMAGE = 2.0F;
    @Nullable
    private UUID ownerUUID;
    private int ticks;

    public MiniTornadoEntity(EntityType<? extends MiniTornadoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public MiniTornadoEntity(Level level, LivingEntity owner, Vec3 center) {
        this(DarkBlueMoonAddon.MINI_WATER_TORNADO.get(), level);
        ownerUUID = owner.getUUID();
        setPos(center.x, center.y, center.z);
    }

    public int getTickCount() {
        return ticks;
    }

    @Nullable
    public LivingEntity getOwner() {
        if (ownerUUID == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(ownerUUID) instanceof LivingEntity living ? living : null;
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (level().isClientSide()) {
            return;
        }
        if (ticks >= LIFESPAN) {
            discard();
            return;
        }
        LivingEntity owner = getOwner();
        if (owner == null) {
            discard();
            return;
        }
        Vec3 motion = getDeltaMovement();
        if (motion.lengthSqr() > 0.001) {
            setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        }
        if (ticks == 1) {
            playSound(DbmWater.randomWaterUseSound(random), 1.0F, 1.0F);
        }
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RADIUS),
                e -> e != owner && e.isAlive())) {
            Vec3 toCenter = position().subtract(target.position());
            double dist = toCenter.length();
            if (dist < 0.5) {
                continue;
            }
            target.setDeltaMovement(target.getDeltaMovement().add(toCenter.normalize().scale(0.35)));
            target.hurtMarked = true;
            target.hurt(damageSources().magic(), DAMAGE);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false, false));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.hasUUID("OwnerUUID")) {
            ownerUUID = nbt.getUUID("OwnerUUID");
        }
        ticks = nbt.getInt("TickCount");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        if (ownerUUID != null) {
            nbt.putUUID("OwnerUUID", ownerUUID);
        }
        nbt.putInt("TickCount", ticks);
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
