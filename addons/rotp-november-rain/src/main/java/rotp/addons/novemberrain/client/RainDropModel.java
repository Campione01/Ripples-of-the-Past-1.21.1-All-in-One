package rotp.addons.novemberrain.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.novemberrain.NovemberRainAddon;
import rotp.addons.novemberrain.entity.RainDropEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/** The remake's RainDropModel: one 1x1x1 cube at the origin on a 16x16 texture. */
public final class RainDropModel extends EntityModel<RainDropEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(NovemberRainAddon.id("rain_drop"), "main");

    private final ModelPart rainDrop;

    public RainDropModel(ModelPart root) {
        this.rainDrop = root.getChild("rain_drop");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
                "rain_drop",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(RainDropEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {}

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
            int color) {
        rainDrop.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
