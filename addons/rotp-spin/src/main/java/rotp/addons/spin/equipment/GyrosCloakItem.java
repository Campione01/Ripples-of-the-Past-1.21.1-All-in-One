package rotp.addons.spin.equipment;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class GyrosCloakItem extends Item {
    public GyrosCloakItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.CHEST;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) return InteractionResultHolder.pass(stack);
        player.setItemSlot(EquipmentSlot.CHEST, stack.copy());
        stack.setCount(0);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static boolean isWornOnChest(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof GyrosCloakItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        tooltip.add(Component.translatable("item.rotp_spin.gyros_cloak.usage").withStyle(ChatFormatting.GRAY));
    }
}
