package rotp.addons.spin.equipment;

import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

final class SpinCurios {
    private SpinCurios() {}

    static ItemStack find(Player player, Predicate<ItemStack> filter) {
        return CuriosApi.getCuriosInventory(player).flatMap(inventory -> inventory.findFirstCurio(filter))
                .map(SlotResult::stack).orElse(ItemStack.EMPTY);
    }
}
