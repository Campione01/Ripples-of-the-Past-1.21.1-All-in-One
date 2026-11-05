package rotp.addons.whitesnake.buffdisc;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * PATCH1 AnvilRecipeHandler: two Buff discs that carry effects fuse into one disc with the effects of both,
 * for 3 levels per effect on the left disc plus one (at most 100) and no extra material.
 */
@EventBusSubscriber(modid = WhitesnakeAddon.MOD_ID)
public final class AnvilRecipeHandler {
    private static final int MAX_COST = 100;

    private AnvilRecipeHandler() {}

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!DiscItem.canFuse(left, right)) {
            return;
        }
        int cost = Math.max(
                1,
                Math.min(DiscItem.effectCount(left) * 3 + 1, MAX_COST));
        ItemStack result = DiscItem.fuseDiscs(left, right);
        if (!result.isEmpty()) {
            event.setOutput(result);
            event.setCost(cost);
            event.setMaterialCost(0);
        }
    }
}
