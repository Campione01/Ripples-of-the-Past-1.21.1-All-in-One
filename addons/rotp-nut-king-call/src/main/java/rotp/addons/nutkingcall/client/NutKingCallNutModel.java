package rotp.addons.nutkingcall.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.nutkingcall.NutKingCallAddon;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.world.entity.Entity;

/** 1.16 NutKingCallNutModel: a one-pixel cube on an 8x8 texture, turned with the nut. */
public final class NutKingCallNutModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(NutKingCallAddon.id("nut_king_call_nut"), "main");

    private final ModelPart bone;

    public NutKingCallNutModel(ModelPart root) {
        this.bone = root.getChild("bone");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("bone",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, CubeDeformation.NONE),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 8, 8);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        bone.yRot = (float) Math.toRadians(-netHeadYaw);
        bone.xRot = (float) Math.toRadians(headPitch);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertices, int packedLight, int packedOverlay,
            int color) {
        bone.render(poseStack, vertices, packedLight, packedOverlay, color);
    }
}
