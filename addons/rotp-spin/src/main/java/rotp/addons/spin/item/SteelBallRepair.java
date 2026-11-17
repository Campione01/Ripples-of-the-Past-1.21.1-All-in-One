package rotp.addons.spin.item;

import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import rotp.addons.spin.config.SpinConfig;

@EventBusSubscriber(modid = "rotp_spin")
public final class SteelBallRepair {
    private SteelBallRepair() {}

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || !(left.getItem() instanceof SteelBallItem) || !SteelBallItem.isChipped(left)) {
            return;
        }
        Item material = repairMaterial();
        if (material != null && right.is(material) && right.getCount() >= left.getCount()) {
            ItemStack result = left.copy();
            SteelBallItem.setChipped(result, false);
            event.setOutput(result);
            event.setMaterialCost(left.getCount());
            event.setCost(SpinConfig.BALL_REPAIR_LEVEL_COST.get());
        }
    }

    @Nullable
    static Item repairMaterial() {
        ResourceLocation id = ResourceLocation.tryParse(SpinConfig.BALL_REPAIR_MATERIAL.get());
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }
}
