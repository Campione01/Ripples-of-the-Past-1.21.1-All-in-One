package rotp.addons.darkbluemoon.client;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * 1.16 BarnacleNecklaceRenderer (a GeckoLib armour renderer): the armorBody bone of barnacle_necklace.geo.json, a body
 * shell with the pendant, worn on the body and following it.
 */
public class BarnacleNecklaceClientExtensions implements IClientItemExtensions {
    @Nullable
    private HumanoidModel<LivingEntity> model;
    private boolean loaded;

    @Override
    public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
            EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
        if (!loaded) {
            loaded = true;
            ModelPart body = DarkBlueMoonModels.loadGeoBone(Minecraft.getInstance().getResourceManager(),
                    "barnacle_necklace", "armorBody");
            if (body != null) {
                model = new HumanoidModel<>(new ModelPart(List.of(), Map.of(
                        "head", empty(),
                        "hat", empty(),
                        "body", body,
                        "right_arm", empty(),
                        "left_arm", empty(),
                        "right_leg", empty(),
                        "left_leg", empty())));
            }
        }
        return equipmentSlot == EquipmentSlot.CHEST && model != null ? model : original;
    }

    private static ModelPart empty() {
        return new ModelPart(List.of(), Map.of());
    }
}
