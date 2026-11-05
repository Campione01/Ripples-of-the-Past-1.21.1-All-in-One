package rotp.addons.darkbluemoon.entity;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.DarkBlueMoonConfig;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 1.16 ScaleShotEntity: the user's straight, gravity-free scale, stronger and longer-lived when fired in water. */
public class ScaleShotEntity extends ModdedProjectileEntity {
    private static final Vec3 OFFSET = new Vec3(0.0, -0.3, 0.75);
    private boolean inWaterMode;

    public ScaleShotEntity(LivingEntity shooter, Level level, boolean inWaterMode) {
        super(DarkBlueMoonAddon.SCALE_SHOT_ENTITY.get(), shooter, level);
        this.inWaterMode = inWaterMode;
    }

    public ScaleShotEntity(EntityType<? extends ScaleShotEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        if (!level().isClientSide()) {
            LivingEntity owner = getOwner();
            if (owner == null || owner instanceof StandEntity) {
                discard();
                return;
            }
        }
        super.tick();
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    @Override
    protected float getBaseDamage() {
        return (float) DarkBlueMoonConfig.getDouble(inWaterMode
                ? DarkBlueMoonConfig.SCALE_SHOT_WATER_DAMAGE : DarkBlueMoonConfig.SCALE_SHOT_LAND_DAMAGE);
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return inWaterMode ? 1.5F : 0.5F;
    }

    @Override
    public int ticksLifespan() {
        return DarkBlueMoonConfig.getInt(inWaterMode
                ? DarkBlueMoonConfig.SCALE_SHOT_WATER_LIFESPAN : DarkBlueMoonConfig.SCALE_SHOT_LAND_LIFESPAN);
    }

    @Override
    protected boolean hasGravity() {
        return false;
    }

    @Override
    protected boolean constVelocity() {
        return true;
    }

    @Override
    protected Vec3 getOwnerRelativeOffset() {
        return OFFSET;
    }

    @Override
    protected DamageSource getDamageSource(LivingEntity owner) {
        if (owner instanceof StandEntity stand) {
            owner = stand.getUser();
        }
        return super.getDamageSource(owner);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("InWaterMode", inWaterMode);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        inWaterMode = nbt.getBoolean("InWaterMode");
    }
}
