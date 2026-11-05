package rotp.addons.beachboy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.client.BeachBoyClientReel;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.addons.beachboy.item.BeachBoyRodItem;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 1.16 BeachBoyGeoRenderer: the rod's geo model with the cast texture while the hook is out (or a lash swings), its
 * reel ("roll" bone) turned by the line length, smoothly (12 per second).
 */
public class BeachBoyRodRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE = BeachBoyAddon.id("textures/item/beach_boy.png");
    private static final ResourceLocation CAST_TEXTURE = BeachBoyAddon.id("textures/item/beach_boy_cast.png");
    private static final float ROLL_SMOOTH_SPEED = 12.0F;
    private float currentRoll;
    private long lastRollNs;

    public BeachBoyRodRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ModelPart model = BeachBoyModels.INSTANCE.rod();
        if (model == null) {
            return;
        }
        ModelPart roll = BeachBoyModels.INSTANCE.rodRoll();
        if (roll != null) {
            float length = resolveReel(stack);
            float target = isLocalHeldStack(stack) ? BeachBoyClientReel.angleFor(length) : BeachBoyClientReel.reelToRoll(length);
            currentRoll = damp(currentRoll, target);
            roll.xRot = -currentRoll;
            roll.yRot = 0.0F;
            roll.zRot = 0.0F;
        }
        ResourceLocation texture = resolveCast(stack) ? CAST_TEXTURE : TEXTURE;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.51F, 0.5F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        model.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), packedLight, packedOverlay);
        poseStack.popPose();
    }

    private float damp(float current, float target) {
        long now = System.nanoTime();
        if (lastRollNs == 0L) {
            lastRollNs = now;
            return target;
        }
        float dt = Mth.clamp((now - lastRollNs) / 1.0E9F, 0.0F, 0.08F);
        lastRollNs = now;
        float alpha = 1.0F - (float) Math.exp(-ROLL_SMOOTH_SPEED * dt);
        return current + (target - current) * alpha;
    }

    private static boolean resolveCast(ItemStack stack) {
        if (isLocalHeldStack(stack)) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (BeachBoyStandUtil.findHook(player) != null) {
                return true;
            }
            BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(player);
            return stand != null && stand.getWhipTicks() > 0;
        }
        return BeachBoyRodItem.isCast(stack);
    }

    private static float resolveReel(ItemStack stack) {
        if (isLocalHeldStack(stack)) {
            LocalPlayer player = Minecraft.getInstance().player;
            BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(player);
            if (hook != null) {
                return hook.getVisualReelLength();
            }
            BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(player);
            if (stand != null) {
                return stand.getMaxLineLength();
            }
        }
        return BeachBoyRodItem.getReel(stack);
    }

    private static boolean isLocalHeldStack(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && stack != null && !stack.isEmpty()
                && (stack == player.getMainHandItem() || stack == player.getOffhandItem());
    }
}
