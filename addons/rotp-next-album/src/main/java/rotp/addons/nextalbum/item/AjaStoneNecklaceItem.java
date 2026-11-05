package rotp.addons.nextalbum.item;

import java.util.List;
import java.util.function.Consumer;

import rotp.addons.nextalbum.client.render.AjaStoneNecklaceRenderer;
import rotp.addons.nextalbum.compat.AjaNecklaceCurios;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class AjaStoneNecklaceItem
        extends ArmorItem implements GeoItem {
    public static final String CURIO_SLOT = "necklace";

    private final AnimatableInstanceCache cache =
            GeckoLibUtil.createInstanceCache(this);

    public AjaStoneNecklaceItem(Item.Properties properties) {
        super(ArmorMaterials.LEATHER, Type.HELMET, properties);
    }

    public static boolean isEquipped(
            LivingEntity entity, Item item) {
        if (entity == null) {
            return false;
        }
        if (entity.getItemBySlot(EquipmentSlot.HEAD).is(item)) {
            return true;
        }
        return AjaNecklaceCurios.isLoaded()
                && AjaNecklaceCurios.isEquipped(entity, item);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.nextalbum.aja_stone_necklace.tooltips",
                        Component.translatable("key.sneak"),
                        Component.translatable("key.mouse.right"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_nextalbum.aja_stone_necklace"
                                + ".reference_quote")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void registerControllers(
            AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(
            Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<AjaStoneNecklaceItem> renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?>
                    getGeoArmorRenderer(
                            @Nullable T livingEntity,
                            ItemStack itemStack,
                            @Nullable EquipmentSlot equipmentSlot,
                            @Nullable HumanoidModel<T> original) {
                if (renderer == null) {
                    renderer = new AjaStoneNecklaceRenderer();
                }
                return renderer;
            }
        });
    }
}
