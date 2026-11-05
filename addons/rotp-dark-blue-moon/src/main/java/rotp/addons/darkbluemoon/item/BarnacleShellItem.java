package rotp.addons.darkbluemoon.item;

import javax.annotation.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/** 1.16 BarnacleShellItem (burns 200 ticks) and CookedBarnacleShellItem (400 ticks). */
public class BarnacleShellItem extends Item {
    private final int burnTime;

    public BarnacleShellItem(Properties properties, int burnTime) {
        super(properties);
        this.burnTime = burnTime;
    }

    @Override
    public int getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType) {
        return burnTime;
    }
}
