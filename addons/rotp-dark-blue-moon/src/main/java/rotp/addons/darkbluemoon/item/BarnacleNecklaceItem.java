package rotp.addons.darkbluemoon.item;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/**
 * 1.16 BarnacleNecklaceItem: a chest piece with leather's protection that cuts the barnacle cost of the barnacle skills
 * (BarnacleItemHelper.getRandomCost). Its look, the GeckoLib armorBody model, is drawn by the client extensions.
 */
public class BarnacleNecklaceItem extends ArmorItem {
    public static final ResourceLocation TEXTURE = DarkBlueMoonAddon.id("textures/models/armor/barnacle_necklace.png");

    public BarnacleNecklaceItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, ArmorItem.Type.CHESTPLATE, properties);
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer,
            boolean innerModel) {
        return TEXTURE;
    }
}
