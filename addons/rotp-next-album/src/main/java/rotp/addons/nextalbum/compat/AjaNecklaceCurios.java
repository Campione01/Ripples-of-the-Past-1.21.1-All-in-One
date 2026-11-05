package rotp.addons.nextalbum.compat;

import rotp.addons.nextalbum.item.AjaStoneNecklaceItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

// Next Album 0.1.13 AjaStoneNecklaceCurios: Curios is optional. Only this
// class (and the client renderer hook) touches the Curios API, and only
// after ModList says Curios is loaded.
public final class AjaNecklaceCurios {
    public static final String CURIOS_MOD_ID = "curios";

    private AjaNecklaceCurios() {}

    public static boolean isLoaded() {
        return ModList.get() != null
                && ModList.get().isLoaded(CURIOS_MOD_ID);
    }

    public static void register(Item necklace) {
        CuriosApi.registerCurio(necklace, new ICurioItem() {
            @Override
            public boolean canEquip(
                    SlotContext slotContext, ItemStack stack) {
                return AjaStoneNecklaceItem.CURIO_SLOT.equals(
                        slotContext.identifier());
            }

            @Override
            public boolean canEquipFromUse(
                    SlotContext slotContext, ItemStack stack) {
                return canEquip(slotContext, stack);
            }
        });
    }

    public static boolean isEquipped(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> handler.isEquipped(item))
                .orElse(false);
    }
}
