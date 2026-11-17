package rotp.addons.neo_stand.client;

import rotp.addons.neo_stand.ability.FingerSweepAbility;
import rotp.addons.neo_stand.ability.FingerSweepAbility.FingerSweepAction;
import rotp.core.api.client.render.EntityPostRenderContext;
import rotp.core.api.client.render.EntityPostRenderExtension;
import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The finger of Finger Sweep. The donor drew none, so it lives only here: the
 * hits stay the action's fan, which walls do not stop either.
 */
public final class FingerSweepRenderer
        implements EntityPostRenderExtension {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples",
                    "textures/entity/projectiles/sp_star_finger.png");
    // Star Platinum's right fist at the perform of its heavy_punch clip,
    // through the bone chain: on its centre line, in blocks from its feet
    private static final double FIST_UP = 1.38D;
    private static final double FIST_FORWARD = 1.04D;
    private static final float SEGMENT_LENGTH = 8.0F / 16.0F;

    private final ModelPart tip;
    private final ModelPart segment;

    public FingerSweepRenderer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "tip",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 2.0F),
                PartPose.ZERO);
        root.addOrReplaceChild(
                "segment",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.0F, -1.0F, 1.0F, 2.0F, 1.0F, 8.0F),
                PartPose.ZERO);
        ModelPart baked =
                LayerDefinition.create(mesh, 32, 32).bakeRoot();
        tip = baked.getChild("tip");
        segment = baked.getChild("segment");
    }

    @Override
    public void afterEntityRender(EntityPostRenderContext context) {
        if (!ClientGlobals.canSeeStands
                || !(context.entity() instanceof StandEntity stand)
                || !(stand.getCurStandAction()
                        instanceof FingerSweepAction action)
                || action.isOver()
                || stand.isInvisible()
                        && stand.isInvisibleTo(
                                Minecraft.getInstance().player)) {
            return;
        }
        float partialTick = context.partialTick();
        ActionPhase phase = action.getPhase();
        float phaseTicks = action.getAnimPhaseTick(partialTick);
        float length = (float) FingerSweepAbility.RANGE
                * FingerSweepAbility.fingerReach(phase, phaseTicks);
        float alpha = StandVisualContext.world(
                        stand, stand.modelAlpha.lerp(partialTick))
                .effectiveAlpha();
        if (length <= 0.0F || alpha <= 0.0F) {
            return;
        }

        double angle = Math.toRadians(FingerSweepAbility.sweepAngle(
                phase == ActionPhase.RECOVERY
                        ? FingerSweepAbility.PERFORM_TICKS
                        : phaseTicks));
        Vec3 look = Vec3.directionFromRotation(
                0.0F,
                Mth.rotLerp(partialTick, stand.yRotO, stand.getYRot()));
        Vec3 right = new Vec3(-look.z, 0.0D, look.x);
        Vec3 extent = look.scale(Math.cos(angle))
                .add(right.scale(Math.sin(angle)))
                .scale(length);
        Vec3 body = Vec3.directionFromRotation(
                0.0F,
                Mth.rotLerp(
                        partialTick, stand.yBodyRotO, stand.yBodyRot));
        Vec3 fingertip = new Vec3(
                        body.x * FIST_FORWARD,
                        FIST_UP,
                        body.z * FIST_FORWARD)
                .add(extent);

        StandSkin skin = StandSkinsLoader.getInstance().getSkin(stand);
        ResourceLocation texture = skin != null
                ? skin.getTexture(TEXTURE)
                : TEXTURE;
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(fingertip.x, fingertip.y, fingertip.z);
        // the cubes run from the fingertip back to the fist
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                MathUtil.yRotDegFromVec(extent)));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                MathUtil.xRotDegFromVec(extent)));
        draw(
                poseStack,
                context.buffer().getBuffer(
                        ModRenderTypes.standTranslucentCull(texture)),
                context.packedLight(),
                length,
                alpha);
        poseStack.popPose();
    }

    private void draw(
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            float length,
            float alpha) {
        int color = Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24
                | 0x00FFFFFF;
        tip.render(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                color);
        for (float left = length;
                left >= SEGMENT_LENGTH;
                left -= SEGMENT_LENGTH) {
            segment.render(
                    poseStack,
                    vertices,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    color);
            poseStack.translate(0.0F, 0.0F, SEGMENT_LENGTH);
        }
    }
}
