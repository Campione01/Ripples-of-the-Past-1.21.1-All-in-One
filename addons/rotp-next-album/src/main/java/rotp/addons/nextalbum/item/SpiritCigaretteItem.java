package rotp.addons.nextalbum.item;

import java.util.List;
import java.util.UUID;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.client.ClientCigaretteState;
import rotp.addons.nextalbum.network.CigaretteSmokePayload;
import rotp.addons.nextalbum.state.CigaretteFocus;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Next Album 0.1.13 spirit cigarette: use lights it; held use inhales
 * (burns four times as fast) and the exhale gives 15 Focus ticks per
 * inhaled tick; Sneak+use puts a lit one in the mouth (CigaretteMouth).
 * A lit cigarette burns out in 2400 ticks anywhere in the inventory.
 */
public class SpiritCigaretteItem extends Item {
    public static final String LIT_KEY = "RotpNextAlbumCigLit";
    public static final String BURN_KEY = "RotpNextAlbumCigBurn";
    public static final String INHALE_START_KEY =
            "RotpNextAlbumCigInhaleStart";
    // A lit cigarette keeps its own id so it never stacks with another.
    private static final String ID_KEY = "CigId";
    public static final float BASE_BURN_PER_TICK = 1.0F / 2400.0F;
    private static final float INHALE_BURN_MUL = 4.0F;
    private static final int MIN_INHALE_TICKS = 8;
    private static final int USE_DURATION = 72000;

    public SpiritCigaretteItem(Item.Properties properties) {
        super(properties);
    }

    public static boolean isCigarette(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof SpiritCigaretteItem;
    }

    public static boolean isLit(ItemStack stack) {
        return isCigarette(stack) && data(stack).getBoolean(LIT_KEY);
    }

    public static void setLit(ItemStack stack, boolean lit) {
        if (!isCigarette(stack)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean(LIT_KEY, lit);
            if (lit) {
                if (!tag.hasUUID(ID_KEY)) {
                    tag.putUUID(ID_KEY, UUID.randomUUID());
                }
            } else {
                tag.remove(ID_KEY);
            }
        });
    }

    public static float getBurn(ItemStack stack) {
        return isCigarette(stack)
                ? Mth.clamp(data(stack).getFloat(BURN_KEY), 0.0F, 1.0F)
                : 0.0F;
    }

    public static void setBurn(ItemStack stack, float burn) {
        if (isCigarette(stack)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack,
                    tag -> tag.putFloat(BURN_KEY,
                            Mth.clamp(burn, 0.0F, 1.0F)));
        }
    }

    @Override
    public void inventoryTick(
            ItemStack stack, Level level, Entity entity,
            int slotId, boolean isSelected) {
        if (level.isClientSide()
                || !(entity instanceof LivingEntity living)
                || !isLit(stack)) {
            return;
        }
        boolean using = living.isUsingItem()
                && living.getUseItem() == stack;
        int used = using ? living.getTicksUsingItem() : 0;
        float inhaleStart = using
                ? data(stack).getFloat(INHALE_START_KEY)
                : getBurn(stack);
        boolean consumed = addBurn(stack, level, living,
                BASE_BURN_PER_TICK * (using ? INHALE_BURN_MUL : 1.0F));
        if (consumed && using && used >= MIN_INHALE_TICKS) {
            CigaretteFocus.addFocus(
                    living, used * CigaretteFocus.INHALE_FOCUS_PER_TICK);
            CigaretteSmokePayload.sendAround(living, used);
            if (inhaleStart <= 0.05F
                    && living instanceof ServerPlayer player) {
                award(player, "smoked_the_world");
            }
        }
    }

    public static boolean addBurn(
            ItemStack stack, Level level, LivingEntity holder, float amount) {
        if (!isCigarette(stack) || amount <= 0.0F) {
            return false;
        }
        float next = getBurn(stack) + amount;
        if (next < 1.0F) {
            setBurn(stack, next);
            return false;
        }
        stack.shrink(1);
        if (!stack.isEmpty()) {
            setLit(stack, false);
            setBurn(stack, 0.0F);
        }
        if (!level.isClientSide()) {
            level.playSound(null,
                    holder.getX(), holder.getY(), holder.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
                    0.35F, 1.35F);
            holder.stopUsingItem();
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (CigaretteMouth.getCount(player) >= CigaretteMouth.MAX_MOUTH) {
                return InteractionResultHolder.fail(stack);
            }
            if (!level.isClientSide()) {
                boolean split = stack.getCount() > 1;
                ItemStack toPlace = split ? stack.split(1) : stack;
                boolean justLit = !isLit(toPlace);
                if (justLit) {
                    setLit(toPlace, true);
                }
                if (CigaretteMouth.add(player, toPlace)) {
                    level.playSound(null,
                            player.getX(), player.getY(), player.getZ(),
                            justLit
                                    ? SoundEvents.FLINTANDSTEEL_USE
                                    : SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                            SoundSource.PLAYERS,
                            0.45F, justLit ? 1.15F : 1.2F);
                } else if (split && !toPlace.isEmpty()
                        && !player.addItem(toPlace)) {
                    player.drop(toPlace, false);
                }
            }
            return InteractionResultHolder.sidedSuccess(
                    player.getItemInHand(hand), level.isClientSide());
        }
        if (!isLit(stack)) {
            if (!level.isClientSide()) {
                light(level, player, stack);
            }
            return InteractionResultHolder.sidedSuccess(
                    stack, level.isClientSide());
        }
        float burn = getBurn(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putFloat(INHALE_START_KEY, burn));
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(
            Level level, LivingEntity entity, ItemStack stack,
            int remainingUseDuration) {
        if (level.isClientSide() && isLit(stack)) {
            int used = getUseDuration(stack, entity) - remainingUseDuration;
            if (used > 0 && used % 10 == 0) {
                ClientCigaretteState.spawnInhaleSmoke(entity);
            }
        }
    }

    private static void light(Level level, Player player, ItemStack stack) {
        if (stack.getCount() > 1) {
            stack.shrink(1);
            ItemStack lit = new ItemStack(stack.getItem());
            setLit(lit, true);
            if (!player.addItem(lit)) {
                player.drop(lit, false);
            }
        } else {
            setLit(stack, true);
        }
        level.playSound(null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS,
                0.6F, 1.15F);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return isLit(stack) ? UseAnim.BOW : UseAnim.NONE;
    }

    @Override
    public void releaseUsing(
            ItemStack stack, Level level, LivingEntity entity,
            int timeLeft) {
        int used = getUseDuration(stack, entity) - timeLeft;
        if (used >= MIN_INHALE_TICKS && isLit(stack)
                && !level.isClientSide()) {
            CigaretteFocus.addFocus(
                    entity, used * CigaretteFocus.INHALE_FOCUS_PER_TICK);
            CigaretteSmokePayload.sendAround(entity, used);
        }
    }

    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() == newStack.getItem() && isLit(newStack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(
            ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged
                || oldStack.getItem() != newStack.getItem()
                || isLit(oldStack) != isLit(newStack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return isLit(stack) && getBurn(stack) > 0.02F;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F - getBurn(stack) * 13.0F);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return isLit(stack) ? 0xE07A2F : 0xC8C8C8;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.nextalbum.spirit_cigarette.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "item.nextalbum.spirit_cigarette.tooltip.mouth")
                .withStyle(ChatFormatting.GRAY));
    }

    static void award(ServerPlayer player, String name) {
        AdvancementHolder advancement = player.server.getAdvancements()
                .get(NextAlbumIds.id(name));
        if (advancement != null) {
            player.getAdvancements().award(advancement, name);
        }
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .getUnsafe();
    }
}
