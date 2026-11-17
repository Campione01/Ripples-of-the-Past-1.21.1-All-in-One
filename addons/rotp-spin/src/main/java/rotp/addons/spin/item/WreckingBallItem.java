package rotp.addons.spin.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import rotp.addons.spin.config.SpinConfig;

public final class WreckingBallItem extends SteelBallItem {
    public WreckingBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        if (isChipped(stack)) {
            tooltip.add(Component.translatable("item.rotp_spin.steel_ball.damaged").withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("item.rotp_spin.wrecking_ball.desc",
                SpinConfig.WRECKING_SATELLITES.get()).withStyle(ChatFormatting.GRAY));
    }
}
