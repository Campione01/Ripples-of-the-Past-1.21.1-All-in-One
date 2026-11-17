package rotp.addons.spin.equipment;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import rotp.addons.spin.SpinAddon;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

final class SpinCuriosClient {
    private SpinCuriosClient() {}

    static void register() {
        CuriosRendererRegistry.register(SpinAddon.GYROS_HOLSTER.get(), () -> new Wearable(false));
        CuriosRendererRegistry.register(SpinAddon.GYROS_CLOAK.get(), () -> new Wearable(true));
    }

    private record Wearable(boolean cloak) implements ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slot,
                PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
                float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
            if (!(parent.getModel() instanceof HumanoidModel<?> model)) return;
            if (cloak) {
                LivingEntity wearer = slot.entity();
                if (!(wearer instanceof Player) || !GyrosCloakItem.isWornOnChest(wearer)) {
                    SpinWearableRenderer.renderCloak(pose, buffers, light, wearer, model, partialTick, age);
                }
            }
            else {
                SpinWearableRenderer.renderHolster(pose, buffers, light, model, stack);
            }
        }
    }
}
