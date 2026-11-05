package rotp.addons.nutkingcall.entity;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** The shared body of 1.16 NutKingCallNailEntity and NutKingCallNutEntity. */
public abstract class NutKingCallProjectile extends ModdedProjectileEntity {
    public static final int LIFESPAN_TICKS = 200;

    protected NutKingCallProjectile(EntityType<? extends NutKingCallProjectile> type, Level level) {
        super(type, level);
    }

    protected NutKingCallProjectile(
            EntityType<? extends NutKingCallProjectile> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    protected abstract void placeOnBlock(Level level, BlockPos hitPos, Direction face);

    protected abstract void attachTo(Level level, LivingEntity target);

    @Override
    protected float getMaxHardnessBreakable() {
        return 0.0F;
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    @Override
    public int ticksLifespan() {
        return LIFESPAN_TICKS;
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (!level().isClientSide()) {
            placeOnBlock(level(), hit.getBlockPos(), hit.getDirection());
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide()) {
            discard();
            return;
        }
        Entity hitEntity = hit.getEntity();
        if (!(hitEntity instanceof LivingEntity target)) {
            super.onHitEntity(hit);
            discard();
            return;
        }
        boolean armorTookIt = damageArmorFirst(target, getBaseDamage());
        if (!armorTookIt) {
            super.onHitEntity(hit);
        }
        attachTo(level(), target);
        discard();
    }

    // 1.16: a damageable chestplate takes the hit's durability instead of its wearer; one it breaks lets the hit through
    private static boolean damageArmorFirst(LivingEntity target, float damage) {
        ItemStack chest = target.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty() || !chest.isDamageableItem()) {
            return false;
        }
        int durabilityDamage = Math.max(1, (int) damage);
        int newDamage = chest.getDamageValue() + durabilityDamage;
        if (newDamage >= chest.getMaxDamage()) {
            chest.setDamageValue(chest.getMaxDamage() - 1);
            target.onEquippedItemBroken(chest.getItem(), EquipmentSlot.CHEST);
            target.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            return false;
        }
        chest.setDamageValue(newDamage);
        return true;
    }
}
