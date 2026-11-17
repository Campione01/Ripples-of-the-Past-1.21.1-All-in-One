package rotp.addons.spin.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import rotp.addons.spin.entity.SpunBlockEntity;
import rotp.addons.spin.entity.SpunItemEntity;

public final class SpunBlockRenderer extends EntityRenderer<SpunBlockEntity> {
    private static final float DEGREES_PER_TICK = 40;

    public SpunBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpunBlockEntity entity, float yaw, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light) {
        BlockState state = entity.getBlockState();
        if (!state.isAir()) {
            poses.pushPose();
            poses.translate(-0.5, 0, -0.5);
            poses.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * DEGREES_PER_TICK));
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poses, buffers, light, OverlayTexture.NO_OVERLAY);
            poses.popPose();
        }
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SpunBlockEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    public static final class SpunItemRenderer extends EntityRenderer<SpunItemEntity> {
        public SpunItemRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(SpunItemEntity entity, float yaw, float partialTick, PoseStack poses,
                MultiBufferSource buffers, int light) {
            ItemStack stack = entity.getItem();
            if (!stack.isEmpty()) {
                poses.pushPose();
                poses.mulPose(entityRenderDispatcher.cameraOrientation());
                poses.mulPose(Axis.YP.rotationDegrees(180));
                poses.mulPose(Axis.ZP.rotationDegrees((entity.tickCount + partialTick) * DEGREES_PER_TICK));
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light,
                        OverlayTexture.NO_OVERLAY, poses, buffers, entity.level(), entity.getId());
                poses.popPose();
            }
            super.render(entity, yaw, partialTick, poses, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(SpunItemEntity entity) {
            return InventoryMenu.BLOCK_ATLAS;
        }
    }
}
