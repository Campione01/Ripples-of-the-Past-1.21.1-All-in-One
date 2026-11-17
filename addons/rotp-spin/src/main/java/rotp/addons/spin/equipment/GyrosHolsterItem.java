package rotp.addons.spin.equipment;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.item.SteelBallItem;

public final class GyrosHolsterItem extends Item {
    public static final String THROW_KEY = "key.rotp_spin.holster_throw";

    public GyrosHolsterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack holster = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            boolean changed = player.isShiftKeyDown() ? unload(player, holster) : load(player, holster);
            if (changed) {
                level.playSound(null, player, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            player.displayClientMessage(ballCountText(holster), true);
        }
        return InteractionResultHolder.sidedSuccess(holster, level.isClientSide());
    }

    private static boolean load(Player player, ItemStack holster) {
        boolean changed = false;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && hasSpace(holster); i++) {
            ItemStack stack = inventory.getItem(i);
            while (insertBall(holster, stack)) {
                stack.shrink(1);
                changed = true;
            }
        }
        if (changed) inventory.setChanged();
        return changed;
    }

    private static boolean unload(Player player, ItemStack holster) {
        List<ItemStack> balls = contents(holster);
        if (balls.isEmpty()) return false;
        setContents(holster, List.of());
        for (ItemStack ball : balls.reversed()) {
            if (!player.getInventory().add(ball)) player.drop(ball, false);
        }
        player.getInventory().setChanged();
        return true;
    }

    private static int capacity() {
        return SpinConfig.HOLSTER_CAPACITY.get();
    }

    static List<ItemStack> contents(ItemStack holster) {
        return new ArrayList<>(holster.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .nonEmptyStream().map(ItemStack::copy).toList());
    }

    static void setContents(ItemStack holster, List<ItemStack> contents) {
        if (contents.isEmpty()) holster.remove(DataComponents.CONTAINER);
        else holster.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
    }

    public static int getBallCount(ItemStack holster) {
        return (int) holster.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().count();
    }

    public static boolean hasSpace(ItemStack holster) {
        return holster.getItem() instanceof GyrosHolsterItem && getBallCount(holster) < capacity();
    }

    public static boolean insertBall(ItemStack holster, ItemStack ball) {
        if (ball.isEmpty() || !(ball.getItem() instanceof SteelBallItem) || !hasSpace(holster)) return false;
        List<ItemStack> balls = contents(holster);
        balls.add(ball.copyWithCount(1));
        setContents(holster, balls);
        return true;
    }

    private static Component ballCountText(ItemStack holster) {
        return Component.translatable("item.rotp_spin.gyros_holster.balls", getBallCount(holster), capacity());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        tooltip.add(ballCountText(stack).copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rotp_spin.gyros_holster.throw_hint", Component.keybind(THROW_KEY))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rotp_spin.gyros_holster.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
