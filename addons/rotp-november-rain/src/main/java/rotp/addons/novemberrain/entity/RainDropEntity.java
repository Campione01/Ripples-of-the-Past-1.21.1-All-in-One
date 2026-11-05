package rotp.addons.novemberrain.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.novemberrain.NovemberRainAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The remake's rain_drop (RainDropEntity): a throwable without gravity that lives 20 ticks, hurts the first entity it
 * meets that is neither its thrower nor a Stand, and breaks a block it meets whose hardness is 10 or less (with drops)
 * where the server lets its thrower break it (the core's block-breaking game rule and JojoModUtil.canEntityDestroy; the
 * remake asked neither).
 */
public class RainDropEntity extends ThrowableProjectile {
    public static final float DEFAULT_DAMAGE = 3.5F;
    public static final float MAX_BLOCK_HARDNESS = 10.0F;
    public static final int LIFETIME_TICKS = 20;

    private float damage = DEFAULT_DAMAGE;

    public RainDropEntity(EntityType<? extends RainDropEntity> type, Level level) {
        super(type, level);
    }

    public RainDropEntity(Level level, LivingEntity owner) {
        super(NovemberRainAddon.RAIN_DROP.get(), owner, level);
        setNoGravity(true);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public float getDamage() {
        return damage;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    // the remake replaced the whole of onHit
    @Override
    protected void onHit(HitResult result) {
        if (level().isClientSide()) {
            return;
        }
        if (result.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) result).getEntity();
            Entity owner = getOwner();
            if (target != owner && !(target instanceof StandEntity)) {
                target.hurt(damageSources().thrown(this, owner), damage);
                discard();
            }
        }
        else if (result.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) result).getBlockPos();
            BlockState state = level().getBlockState(pos);
            float blockHardness = state.getDestroySpeed(level(), pos);
            if (blockHardness != -1.0F && blockHardness <= MAX_BLOCK_HARDNESS
                    && level() instanceof ServerLevel serverLevel && getOwner() instanceof LivingEntity thrower
                    && JojoModUtil.canEntityDestroy(serverLevel, pos, state, thrower)) {
                level().destroyBlock(pos, true);
            }
            discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > LIFETIME_TICKS) {
            discard();
        }
    }
}
