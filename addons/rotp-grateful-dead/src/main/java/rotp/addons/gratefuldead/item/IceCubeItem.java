package rotp.addons.gratefuldead.item;

import java.util.List;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.compat.TemperatureCompat;
import rotp.addons.gratefuldead.state.AgingRateState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Next Album 0.1.13 IceCubeItem: held (also by a Sex Pistols member next to
 * you) it lowers the aging-rate window; drinking it cools for up to 6
 * seconds; an unmelted cube in the inventory keeps the aging aura off the
 * living items there. It melts with time, faster held, used or in the heat.
 */
public final class IceCubeItem extends Item {
    public static final int USE_DURATION = 32;
    public static final float BASE_MELT_PER_TICK = 1.0F / 1800.0F;
    public static final float HELD_MELT_MULTIPLIER = 2.4F;
    public static final float USE_MELT_MULTIPLIER = 6.0F;
    public static final float USE_COOLING = 0.4F;
    public static final double CARRIED_ICE_RANGE = 2.5D;
    public static final int BAR_COLOR = 0x7EC8E3;

    public IceCubeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean isIceCube(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IceCubeItem;
    }

    public static boolean isHolding(Player player) {
        return isIceCube(player.getMainHandItem()) || isIceCube(player.getOffhandItem());
    }

    public static boolean isProvidingCooling(LivingEntity entity) {
        if (entity instanceof Player player && isHolding(player)) {
            return true;
        }
        return entity != null && !entity.level().getEntitiesOfClass(
                StandEntity.class,
                entity.getBoundingBox().inflate(CARRIED_ICE_RANGE),
                stand -> stand.isAlive() && isIceCube(stand.getMainHandItem())).isEmpty();
    }

    public static boolean protectsInventory(Player player) {
        if (player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isIceCube(stack) && melt(stack) < 1.0F) {
                return true;
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        return isIceCube(carried) && melt(carried) < 1.0F;
    }

    public static float useCooling(Player player) {
        return player.isUsingItem() && isIceCube(player.getUseItem()) ? USE_COOLING : 0.0F;
    }

    public static float melt(ItemStack stack) {
        return isIceCube(stack)
                ? Mth.clamp(stack.getOrDefault(GratefulDeadAddon.ICE_MELT, 0.0F), 0.0F, 1.0F)
                : 0.0F;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof LivingEntity living)) {
            return;
        }
        // A Stand (a Sex Pistols member) only ticks the cube it carries.
        boolean held = selected || living.getOffhandItem() == stack || living instanceof StandEntity;
        boolean using = living.isUsingItem() && living.getUseItem() == stack;
        float meltMultiplier = Mth.clamp(
                1.0F + 0.85F * TemperatureCompat.environmentNorm(level, living.blockPosition(), living),
                0.12F,
                2.1F);
        if (using) {
            meltMultiplier *= USE_MELT_MULTIPLIER;
        }
        else if (held) {
            meltMultiplier *= HELD_MELT_MULTIPLIER;
        }
        addMelt(stack, level, living, BASE_MELT_PER_TICK * meltMultiplier);
    }

    public static void addMelt(ItemStack stack, Level level, LivingEntity holder, float amount) {
        if (!isIceCube(stack) || amount <= 0.0F) {
            return;
        }
        float next = melt(stack) + amount;
        if (next < 1.0F) {
            stack.set(GratefulDeadAddon.ICE_MELT, next);
            return;
        }
        stack.shrink(1);
        if (!stack.isEmpty()) {
            stack.remove(GratefulDeadAddon.ICE_MELT);
        }
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.35F, 1.35F);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        applyLeftoverCool(entity, 1.0F);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int used = USE_DURATION - timeLeft;
        if (used >= 8) {
            applyLeftoverCool(entity, used / (float) USE_DURATION);
        }
    }

    private static void applyLeftoverCool(LivingEntity entity, float strength) {
        if (entity.level().isClientSide()) {
            return;
        }
        AgingRateState.get(entity).startIceCool(
                0.3F * Mth.clamp(strength, 0.25F, 1.0F),
                Math.max(24, (int) (120.0F * strength)));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return melt(stack) > 0.02F;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * (1.0F - melt(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.nextalbum.ice_cube.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.nextalbum.ice_cube.tooltip.inventory").withStyle(ChatFormatting.GRAY));
    }
}
