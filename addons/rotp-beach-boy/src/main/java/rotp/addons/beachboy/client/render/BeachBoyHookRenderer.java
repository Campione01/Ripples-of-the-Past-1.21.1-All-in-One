package rotp.addons.beachboy.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.client.BeachBoyClientInput;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.addons.beachboy.util.BeachBoyCombatUtil;
import rotp.addons.beachboy.util.BeachBoyLinePath;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.entityglow.EntityGlowChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 1.16 BeachBoyHookRenderer: the hook model (hidden while it is in a creature or set as a trap), the pink line from
 * the rod tip, a short stub to the ground for a trap, and the trap block's outline in Beach Boy's colour for its owner.
 */
public class BeachBoyHookRenderer extends EntityRenderer<BeachBoyHookEntity> {
    private static final ResourceLocation TEXTURE = BeachBoyAddon.id("textures/entity/beach_boy_hook.png");
    private static final ResourceLocation LINE_TEXTURE = BeachBoyAddon.id("textures/misc/white.png");
    private static final RenderType HOOK_TYPE = BeachBoyRenderTypes.hook(TEXTURE);
    private static final RenderType HOOK_SEE_THROUGH = BeachBoyRenderTypes.hookSeeThrough(TEXTURE);
    private static final RenderType LINE_TYPE = BeachBoyRenderTypes.line(LINE_TEXTURE);
    private static final int FULLBRIGHT = LightTexture.FULL_BRIGHT;
    private static final float LINE_HALF_WIDTH = 0.016F;

    public BeachBoyHookRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(BeachBoyHookEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(BeachBoyHookEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        Entity owner = entity.getOwner();
        if (entity.isTrapArmed()) {
            renderTrapBlockGlow(entity, partialTicks, poseStack, buffer);
            if (owner != null && !isRemoteCameraFor(entity)) {
                renderTrapStub(entity, owner, partialTicks, poseStack, buffer);
            }
        }
        else {
            if (!isRemoteCameraFor(entity) && entity.getHookedEntity() == null) {
                renderHookModel(entity, partialTicks, poseStack, buffer, FULLBRIGHT, HOOK_TYPE);
            }
            if (owner != null && !isRemoteCameraFor(entity) && !isWhippingHookedTarget(entity, owner)) {
                renderLine(entity, owner, partialTicks, poseStack, buffer, FULLBRIGHT);
            }
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, FULLBRIGHT);
    }

    // 1.16 drew the trap block into the outline pass with the hook made glowing for its owner
    private void renderTrapBlockGlow(BeachBoyHookEntity entity, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player != entity.getOwner()) {
            return;
        }
        Optional<BlockPos> trap = entity.getTrapBlockPos();
        if (trap.isEmpty()) {
            return;
        }
        BlockPos pos = trap.get();
        BlockState state = entity.level().getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        EntityGlowChannel.GE_LIFE_DETECTOR.apply(entity,
                OptionalInt.of(BeachBoyStandUtil.beachBoyColor(StandPower.get(mc.player))), 2);
        Vec3 origin = interpolatedOrigin(entity, partialTicks);
        poseStack.pushPose();
        poseStack.translate(pos.getX() - origin.x, pos.getY() - origin.y, pos.getZ() - origin.z);
        VertexConsumer builder = buffer.getBuffer(RenderType.outline(TextureAtlas.LOCATION_BLOCKS));
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        dispatcher.getModelRenderer().renderModel(poseStack.last(), builder, state, dispatcher.getBlockModel(state),
                1.0F, 1.0F, 1.0F, FULLBRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        poseStack.popPose();
    }

    private static boolean isWhippingHookedTarget(BeachBoyHookEntity hook, Entity owner) {
        if (hook.getHookedEntity() == null || !(owner instanceof LivingEntity living)) {
            return false;
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(living);
        return stand != null && stand.getWhipTicks() > 0;
    }

    private static boolean isRemoteCameraFor(BeachBoyHookEntity hook) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        return camera instanceof BeachBoyStandEntity stand ? stand.getUser() == hook.getOwner() : camera == hook;
    }

    public static void renderHookFromWorld(BeachBoyHookEntity hook, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        if (hook.isTrapArmed() || hook.getHookedEntity() != null || mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        Vec3 origin = interpolatedOrigin(hook, partialTicks);
        poseStack.pushPose();
        poseStack.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
        renderHookModel(hook, partialTicks, poseStack, buffer, FULLBRIGHT, HOOK_SEE_THROUGH);
        poseStack.popPose();
    }

    private static void renderHookModel(BeachBoyHookEntity entity, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, RenderType renderType) {
        float yRot = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        float xRot = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot));
        renderGeo(BeachBoyModels.INSTANCE.hook(), poseStack, buffer.getBuffer(renderType), packedLight);
        poseStack.popPose();
    }

    // the core's Gecko parser bakes in entity model space; this puts the geo origin where GeckoLib drew it
    private static void renderGeo(@Nullable ModelPart model, PoseStack poseStack, VertexConsumer consumer, int light) {
        if (model == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        model.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    public static void renderLineFromWorld(BeachBoyHookEntity hook, Entity owner, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, Vec3 camera) {
        Vec3 origin = interpolatedOrigin(hook, partialTicks);
        if (hook.isTrapArmed()) {
            poseStack.pushPose();
            poseStack.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
            renderTrapStub(hook, owner, partialTicks, poseStack, buffer);
            poseStack.popPose();
        }
        else if (!isWhippingHookedTarget(hook, owner)) {
            poseStack.pushPose();
            poseStack.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
            renderLine(hook, owner, partialTicks, poseStack, buffer, FULLBRIGHT);
            poseStack.popPose();
        }
    }

    private static void renderLine(BeachBoyHookEntity hook, Entity owner, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        Vec3 origin = interpolatedOrigin(hook, partialTicks);
        List<Vec3> anchors = new ArrayList<>(hook.getWrapPoints().size() + 2);
        anchors.add(getLineStartForRender(hook, owner, partialTicks));
        anchors.addAll(hook.getWrapPoints());
        anchors.add(hook.getLineEnd(partialTicks));
        List<Vec3> path = BeachBoyLinePath.buildPath(anchors, hook.hasLineBlockCollision());
        renderRibbonRelative(path, origin, poseStack, buffer, packedLight);
    }

    private static Vec3 interpolatedOrigin(BeachBoyHookEntity hook, float partialTicks) {
        return new Vec3(Mth.lerp(partialTicks, hook.xo, hook.getX()), Mth.lerp(partialTicks, hook.yo, hook.getY()),
                Mth.lerp(partialTicks, hook.zo, hook.getZ()));
    }

    private static Vec3 getLineStartForRender(@Nullable BeachBoyHookEntity hook, Entity owner, float partialTicks) {
        if (owner instanceof LivingEntity living) {
            Vec3 firstPerson = BeachBoyClientInput.tryFirstPersonRodTip(living, partialTicks);
            return firstPerson != null ? firstPerson : BeachBoyHookEntity.getRodTip(living, partialTicks);
        }
        return hook != null ? hook.getLineStart(partialTicks) : owner.position();
    }

    /** 1.16 renderWhipFromWorld: the lash's line and, with nothing on the hook, the hook at its tip. */
    public static void renderWhipFromWorld(LivingEntity user, BeachBoyStandEntity stand, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, Vec3 camera) {
        if (stand.getWhipTicks() <= 0) {
            return;
        }
        float progress = 1.0F - Mth.clamp((stand.getWhipTicks() - partialTicks) / BeachBoyCombatUtil.WHIP_ANIM_TICKS,
                0.0F, 1.0F);
        Vec3 origin = getLineStartForRender(null, user, partialTicks);
        List<Vec3> path = BeachBoyCombatUtil.buildWhipArc(origin, user.getViewVector(partialTicks),
                stand.isWhipVertical(), stand.isWhipLeftToRight(), stand.isWhipUpward(), progress,
                BeachBoyCombatUtil.whipRange(stand.isWhipVertical()));
        if (path.size() < 2) {
            return;
        }
        Vec3 start = path.get(0);
        poseStack.pushPose();
        poseStack.translate(start.x - camera.x, start.y - camera.y, start.z - camera.z);
        renderRibbonRelative(path, start, poseStack, buffer, FULLBRIGHT);
        poseStack.popPose();
        if (BeachBoyCombatUtil.hookedLiving(BeachBoyStandUtil.findHook(user)) == null) {
            Vec3 tip = path.get(path.size() - 1);
            renderHookAtWorld(tip, tip.subtract(path.get(path.size() - 2)), poseStack, buffer, camera);
        }
    }

    private static void renderHookAtWorld(Vec3 worldPos, Vec3 direction, PoseStack poseStack, MultiBufferSource buffer,
            Vec3 camera) {
        Vec3 dir = direction.lengthSqr() < 1.0E-10 ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
        float yRot = (float) (Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG);
        float xRot = (float) (Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * Mth.RAD_TO_DEG);
        poseStack.pushPose();
        poseStack.translate(worldPos.x - camera.x, worldPos.y - camera.y, worldPos.z - camera.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(-xRot));
        renderGeo(BeachBoyModels.INSTANCE.hook(), poseStack, buffer.getBuffer(HOOK_SEE_THROUGH), FULLBRIGHT);
        poseStack.popPose();
    }

    private static void renderTrapStub(BeachBoyHookEntity hook, Entity owner, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer) {
        Vec3 origin = interpolatedOrigin(hook, partialTicks);
        Vec3 start = getLineStartForRender(hook, owner, partialTicks);
        List<Vec3> path = new ArrayList<>(2);
        path.add(start);
        path.add(trapStubEnd(owner, start));
        renderRibbonRelative(path, origin, poseStack, buffer, FULLBRIGHT);
    }

    private static Vec3 trapStubEnd(Entity owner, Vec3 start) {
        Vec3 down = start.add(0.0, -2.6, 0.0);
        BlockHitResult hit = owner.level().clip(new ClipContext(start, down, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return new Vec3(start.x, owner.getY(), start.z);
    }

    private static void renderRibbonRelative(List<Vec3> worldPoints, Vec3 origin, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        if (worldPoints.size() < 2) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer ribbon = buffer.getBuffer(LINE_TYPE);
        Vec3 prev = worldPoints.get(0).subtract(origin);
        for (int i = 1; i < worldPoints.size(); i++) {
            Vec3 next = worldPoints.get(i).subtract(origin);
            Vec3 segment = next.subtract(prev);
            if (segment.lengthSqr() < 1.0E-12) {
                prev = next;
                continue;
            }
            Vec3 worldMid = origin.add(prev.add(next).scale(0.5));
            Vec3 side = segment.cross(camera.subtract(worldMid));
            if (side.lengthSqr() < 1.0E-10) {
                side = segment.cross(new Vec3(0.0, 1.0, 0.0));
            }
            if (side.lengthSqr() < 1.0E-10) {
                prev = next;
                continue;
            }
            side = side.normalize().scale(LINE_HALF_WIDTH);
            addRibbonQuad(ribbon, pose, prev.add(side), prev.subtract(side), next.subtract(side), next.add(side),
                    packedLight);
            prev = next;
        }
    }

    private static void addRibbonQuad(VertexConsumer builder, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
            int packedLight) {
        addRibbonVertex(builder, pose, p0, packedLight);
        addRibbonVertex(builder, pose, p1, packedLight);
        addRibbonVertex(builder, pose, p2, packedLight);
        addRibbonVertex(builder, pose, p3, packedLight);
        addRibbonVertex(builder, pose, p3, packedLight);
        addRibbonVertex(builder, pose, p2, packedLight);
        addRibbonVertex(builder, pose, p1, packedLight);
        addRibbonVertex(builder, pose, p0, packedLight);
    }

    // 1.16 colour (255, 164, 219)
    private static void addRibbonVertex(VertexConsumer builder, PoseStack.Pose pose, Vec3 pos, int packedLight) {
        builder.addVertex(pose.pose(), (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(255, 164, 219, 255)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(BeachBoyHookEntity entity) {
        return TEXTURE;
    }
}
