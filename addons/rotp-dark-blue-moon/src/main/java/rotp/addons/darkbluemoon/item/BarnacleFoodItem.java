package rotp.addons.darkbluemoon.item;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ItemLike;

/**
 * 1.16 BarnacleItem, CookedBarnacleItem and BarnacleMeatItem: eaten whatever the hunger (use always starts), for their
 * own use time; finishing has a 10% chance of Nausea (200 ticks, the two whole barnacles only) and a 10% chance of
 * Poison (100 ticks), burps, takes one, and a whole barnacle leaves its shell. 1.16 registered the hunger values of the
 * meat and the cooked barnacle but its finish never applied them; here a player gets them (owner delegation: an
 * intended feature left unwired).
 */
public class BarnacleFoodItem extends Item {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private final int useDuration;
    private final boolean mayNauseate;
    @Nullable
    private final Supplier<? extends ItemLike> shell;

    public BarnacleFoodItem(Properties properties, int useDuration, boolean mayNauseate,
            @Nullable Supplier<? extends ItemLike> shell) {
        super(properties);
        this.useDuration = useDuration;
        this.mayNauseate = mayNauseate;
        this.shell = shell;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return useDuration;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            if (mayNauseate && RANDOM.nextFloat() < 0.1F) {
                entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            }
            if (RANDOM.nextFloat() < 0.1F) {
                entity.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            }
            entity.playSound(SoundEvents.PLAYER_BURP, 1.0F, 1.0F);
        }
        if (entity instanceof Player player) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null && !level.isClientSide()) {
                player.getFoodData().eat(food);
                player.awardStat(Stats.ITEM_USED.get(this));
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            if (shell != null) {
                ItemStack shellStack = new ItemStack(shell.get());
                if (!player.getInventory().add(shellStack)) {
                    player.drop(shellStack, false);
                }
            }
        }
        return stack;
    }
}
