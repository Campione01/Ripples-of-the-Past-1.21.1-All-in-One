package rotp.addons.darkbluemoon.util;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.DarkBlueMoonConfig;
import rotp.addons.darkbluemoon.entity.DarkBlueMoonEntity;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 1.16 BarnacleItemHelper. */
public final class BarnacleItemHelper {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();

    private BarnacleItemHelper() {}

    /**
     * 1.16 asked for the summoned Dark Blue Moon (getStandManifestation), after a press had already summoned it
     * (PowerBaseImpl.onClickAction ran onClick first). Here the press checks before its auto-summon, so the Stand power
     * is asked, which gives the same answer for every press.
     */
    public static boolean hasDarkBlueMoon(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        StandPower power = StandPower.get(player);
        return power != null && power.hasPower()
                && (power.getPowerType() == DarkBlueMoonAddon.DARK_BLUE_MOON.get()
                        || power.getSummonedStandEntity() instanceof DarkBlueMoonEntity);
    }

    public static boolean isHoldingBarnacle(LivingEntity entity) {
        return entity instanceof Player player
                && (player.getMainHandItem().is(DarkBlueMoonAddon.BARNACLE_ITEM.get())
                        || player.getOffhandItem().is(DarkBlueMoonAddon.BARNACLE_ITEM.get()));
    }

    public static boolean hasBarnacleNecklace(LivingEntity entity) {
        return entity instanceof Player player
                && player.getItemBySlot(EquipmentSlot.CHEST).is(DarkBlueMoonAddon.BARNACLE_NECKLACE.get());
    }

    /** The main inventory (1.16 inventory.items) and the off hand. */
    public static int countBarnacle(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(DarkBlueMoonAddon.BARNACLE_ITEM.get())) {
                count += stack.getCount();
            }
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(DarkBlueMoonAddon.BARNACLE_ITEM.get())) {
            count += off.getCount();
        }
        return count;
    }

    public static int getRandomCost(LivingEntity entity) {
        int min = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.BARNACLE_MIN_COST);
        int max = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.BARNACLE_MAX_COST);
        int base = min + RANDOM.nextInt(Math.max(1, max - min + 1));
        if (hasBarnacleNecklace(entity)) {
            int divisor = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.BARNACLE_NECKLACE_COST_DIVISOR);
            base = Math.max(1, base / divisor);
        }
        return base;
    }

    public static boolean consumeBarnacle(LivingEntity entity, int amount) {
        if (!(entity instanceof Player player) || countBarnacle(player) < amount) {
            return false;
        }
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(DarkBlueMoonAddon.BARNACLE_ITEM.get())) {
                int take = Math.min(stack.getCount(), remaining);
                stack.shrink(take);
                remaining -= take;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        if (remaining > 0) {
            ItemStack off = player.getOffhandItem();
            if (off.is(DarkBlueMoonAddon.BARNACLE_ITEM.get())) {
                off.shrink(Math.min(off.getCount(), remaining));
            }
        }
        return true;
    }

    /** In water always; on land only a Dark Blue Moon user holding a barnacle with at least the minimum cost. */
    public static boolean canUseBarnacleSkill(LivingEntity user) {
        if (DbmWater.isInWater(user)) {
            return true;
        }
        return hasDarkBlueMoon(user) && isHoldingBarnacle(user)
                && countBarnacle(user) >= DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.BARNACLE_MIN_COST);
    }

    /** The land cost of a barnacle skill; true when it was paid or none was due (in water). */
    public static boolean payLandCost(LivingEntity user) {
        if (DbmWater.isInWater(user)) {
            return true;
        }
        return consumeBarnacle(user, getRandomCost(user));
    }
}
