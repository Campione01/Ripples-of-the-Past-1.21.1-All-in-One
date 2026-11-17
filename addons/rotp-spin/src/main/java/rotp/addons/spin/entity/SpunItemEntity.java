package rotp.addons.spin.entity;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.config.SpinConfig;

public final class SpunItemEntity extends SpunProjectileEntity implements ItemSupplier {
    private static final EntityDataAccessor<ItemStack> ITEM =
            SynchedEntityData.defineId(SpunItemEntity.class, EntityDataSerializers.ITEM_STACK);

    public SpunItemEntity(EntityType<? extends SpunItemEntity> type, Level level) {
        super(type, level);
    }

    public SpunItemEntity(ServerLevel level, LivingEntity thrower, ItemStack stack) {
        super(SpinAddon.SPUN_ITEM.get(), level, thrower, stack, SpinConfig.ITEM_SPIN_DAMAGE.get());
        entityData.set(ITEM, getPickupItem());
    }

    public static SpunItemEntity prepare(ServerLevel level, LivingEntity thrower, ItemStack stack) {
        SpunItemEntity projectile = new SpunItemEntity(level, thrower, stack);
        projectile.shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), 0,
                SpinConfig.ITEM_SPIN_VELOCITY.get().floatValue(), SpinConfig.BALL_INACCURACY.get().floatValue());
        return projectile;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ITEM, ItemStack.EMPTY);
    }

    @Override
    public ItemStack getItem() {
        return entityData.get(ITEM);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ITEM, tag.contains("item", CompoundTag.TAG_COMPOUND) ? getPickupItem() : ItemStack.EMPTY);
    }
}

/** The legacy RotP item-projectile response shared by the two improvised throws. */
abstract class SpunProjectileEntity extends AbstractArrow {
    protected SpunProjectileEntity(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
    }

    protected SpunProjectileEntity(EntityType<? extends AbstractArrow> type, Level level, LivingEntity owner,
            ItemStack stack, double baseDamage) {
        super(type, owner, level, stack.isEmpty() ? new ItemStack(Items.ARROW) : stack.copyWithCount(1), null);
        setBaseDamage(baseDamage);
        if (stack.isEmpty()) {
            pickup = Pickup.DISALLOWED;
        }
        else if (owner.hasInfiniteMaterials()) {
            pickup = Pickup.CREATIVE_ONLY;
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        // AbstractArrow's NBT requires a nonempty serialization fallback. It is never recoverable.
        return new ItemStack(Items.ARROW);
    }

    @Override
    protected void onHit(HitResult hit) {
        boolean broken = false;
        if (level() instanceof ServerLevel level && getOwner() instanceof LivingEntity owner) {
            ItemStack stack = getPickupItemStackOrigin();
            stack.hurtAndBreak(1, level, owner, item -> {});
            broken = stack.isEmpty();
        }
        super.onHit(hit);
        if (broken) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = hit.getEntity();
        Entity owner = getOwner();
        if (owner instanceof LivingEntity living) {
            living.setLastHurtMob(target);
        }
        boolean enderman = target.getType() == EntityType.ENDERMAN;
        int previousFireTicks = target.getRemainingFireTicks();
        if (isOnFire() && !enderman) {
            target.igniteForSeconds(5);
        }
        DamageSource source = damageSources().arrow(this, owner == null ? this : owner);
        float damage = (float) (getDeltaMovement().length() * getBaseDamage());
        if (target.hurt(source, damage)) {
            onSuccessfulHit(target, owner);
            if (enderman) {
                return;
            }
            if (target instanceof LivingEntity living) {
                EnchantmentHelper.doPostAttackEffects(level, living, source);
                doPostHurtEffects(living);
            }
            playSound(getHitGroundSoundEvent(), 1, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
            changeMovementAfterHit();
        }
        else {
            target.setRemainingFireTicks(previousFireTicks);
            setDeltaMovement(getDeltaMovement().scale(-0.1));
            setYRot(getYRot() + 180);
            yRotO += 180;
            if (getDeltaMovement().lengthSqr() < 1.0E-7) {
                changeMovementAfterHit();
            }
        }
    }

    protected void onSuccessfulHit(Entity target, Entity owner) {}

    protected void changeMovementAfterHit() {
        setDeltaMovement(getDeltaMovement().multiply(-0.01, -0.1, -0.01));
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        var pos = hit.getBlockPos();
        setSoundEvent(level().getBlockState(pos).getSoundType(level(), pos, this).getBreakSound());
        super.onHitBlock(hit);
        shakeTime = 0;
        setNoGravity(false);
        setSoundEvent(getDefaultHitGroundSoundEvent());
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        Entity owner = getOwner();
        return super.canHitEntity(entity)
                && !(entity instanceof Projectile projectile && owner != null && owner.is(projectile.getOwner()));
    }

    @Override
    public boolean isPickable() {
        return isAlive();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return isAlive();
    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity target, DamageSource source) {
        Entity attacker = source.getEntity();
        return attacker == null ? super.calculateHorizontalHurtKnockbackDirection(target, source)
                : DoubleDoubleImmutablePair.of(target.getX() - attacker.getX(), target.getZ() - attacker.getZ());
    }

    @Override
    public void playerTouch(Player player) {
        Entity owner = getOwner();
        if (!level().isClientSide() && isAlive() && (inGround || owner == null || player.is(owner))
                && (inGround || isNoPhysics() || leftOwner) && tryPickup(player)) {
            player.take(this, 1);
            discard();
        }
    }

    @Override
    protected void tickDespawn() {
        if (pickup != Pickup.ALLOWED) {
            super.tickDespawn();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Age", tickCount);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        tickCount = tag.getInt("Age");
    }
}
