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
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.Entity;

/**
 * 1.16 NutKingCallNailModel and NutKingCallAttachedNailModel: a screw head, shaft and tip on an 8x8 texture. The flying
 * nail turns with its rotation; the attached one does not.
 */
public final class NutKingCallNailModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(NutKingCallAddon.id("nut_king_call_nail"), "main");
    /** The nail block's renderer mirrors the head and tip rotations. */
    public static final ModelLayerLocation BLOCK_LAYER =
            new ModelLayerLocation(NutKingCallAddon.id("nut_king_call_nail_block"), "main");

    private final ModelPart bone;
    private final boolean followsRotation;

    public NutKingCallNailModel(ModelPart root, boolean followsRotation) {
        this.bone = root.getChild("bone");
        this.followsRotation = followsRotation;
    }

    public static LayerDefinition createLayer() {
        return createLayer(-1.0F);
    }

    public static LayerDefinition createBlockLayer() {
        return createLayer(1.0F);
    }

    private static LayerDefinition createLayer(float rotationSign) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition bone = mesh.getRoot().addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.ZERO);
        bone.addOrReplaceChild("cube1",
                CubeListBuilder.create().texOffs(3, 0)
                        .addBox(-0.196F, -0.366F, -0.366F, 0.392F, 0.733F, 0.733F, new CubeDeformation(0.1F)),
                PartPose.rotation(0.0F, 0.0F, (float) Math.toRadians(90.0D * rotationSign)));
        bone.addOrReplaceChild("cube2",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.159F, -0.31F, -0.171F, 0.364F, 1.479F, 0.341F, CubeDeformation.NONE),
                PartPose.ZERO);
        bone.addOrReplaceChild("cube3",
                CubeListBuilder.create().texOffs(0, 4)
                        .addBox(-0.118F, -0.15F, -0.171F, 0.3F, 0.3F, 0.3F, CubeDeformation.NONE),
                PartPose.rotation(0.0F, 0.0F, (float) Math.toRadians(47.5D * rotationSign)));
        return LayerDefinition.create(mesh, 8, 8);
    }

    public ModelPart bone() {
        return bone;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        if (followsRotation) {
            bone.yRot = (float) Math.toRadians(-netHeadYaw);
            bone.xRot = (float) Math.toRadians(headPitch);
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertices, int packedLight, int packedOverlay,
            int color) {
        bone.render(poseStack, vertices, packedLight, packedOverlay, color);
    }
}
