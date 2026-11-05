package rotp.addons.echoes.client;

import java.io.Reader;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.parsemodel.ParseModEntityModel;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel.ModelFormat;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.entity.EchoesOnomatopoeiaWordEntity;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;

/**
 * R772 F11: ACT2's word as a thing on a block (the master's OnomatopoeiaBlockRenderer, a GeckoLib projectile
 * renderer): the author's onomatopoeia_block model and texture, seen only by Stand users.
 */
public final class EchoesOnomatopoeiaWordRenderer extends EntityRenderer<EchoesOnomatopoeiaWordEntity> {
    private static final ResourceLocation MODEL = RotpEchoes.id("geo/onomatopoeia_block.geo.json");
    private static final ResourceLocation TEXTURE = RotpEchoes.id("textures/entity/onomatopoeia_block.png");
    @Nullable private final ModelPart model;

    public EchoesOnomatopoeiaWordRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart baked = null;
        Resource resource = context.getResourceManager().getResource(MODEL).orElse(null);
        if (resource != null) {
            try (Reader reader = resource.openAsReader()) {
                baked = ParseModEntityModel.parse(JsonParser.parseReader(reader), ModelFormat.GECKO).bakeRoot();
            }
            catch (Exception e) {
                baked = null;
            }
        }
        this.model = baked;
    }

    @Override
    public void render(EchoesOnomatopoeiaWordEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        StandPower viewer = mc.player != null ? StandPower.get(mc.player) : null;
        if (model == null || viewer == null || !viewer.hasPower()) {
            return;
        }
        poseStack.pushPose();
        // the core's bake, turned upright, is GeckoLib's model half a turn round and 1.5 blocks down; the master's
        // GeckoLib projectile renderer turned the model by yaw - 90
        poseStack.translate(0.0D, 1.5D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()) + 90.0F));
        model.xRot = (float) Math.PI;
        model.render(poseStack, buffer.getBuffer(RenderType.entityCutout(TEXTURE)), packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(EchoesOnomatopoeiaWordEntity entity) {
        return TEXTURE;
    }
}
