package rotp.addons.whitesnake.client;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.entityrender.stand.StandEntityRenderState.ObstructionRenderMode;
import rotp.core.client.entityrender.stand.StandOpacityPolicy;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;

import rotp.addons.whitesnake.disguise.DisguiseTypePolicy;
import rotp.addons.whitesnake.entity.WhitesnakeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class WhitesnakeRenderer extends StandEntityRenderer<
        WhitesnakeEntity,
        WhitesnakeRenderState,
        StandEntityModel<WhitesnakeEntity, WhitesnakeRenderState>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float TRANSFORM_TICKS = 20.0F;
    private static final int MAX_PREVIEW_CACHE = 32;
    // StandOffsetFromUser.createDefault: where the Stand itself stands, behind its user's right.
    private static final Vec3 STAND_PLACE = new Vec3(-0.75D, 0.2D, -0.75D);
    private static WhitesnakeRenderer current;

    private final Map<ResourceLocation, LivingEntity> previewEntities =
            new LinkedHashMap<>(16, 0.75F, true);
    private final Set<ResourceLocation> rejectedRenderers =
            new HashSet<>();
    private ClientLevel previewLevel;
    private boolean renderingDisguise;

    public WhitesnakeRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new WhitesnakeGlintLayer(this));
        current = this;
    }

    /** The pose stack is at the camera's origin, as the level's entities get it. */
    static void renderWithoutStand(
            AbstractClientPlayer user,
            ResourceLocation typeId,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 camera,
            float partialTick) {
        if (current != null) {
            current.renderBesideUser(
                    user,
                    typeId,
                    poseStack,
                    bufferSource,
                    camera,
                    partialTick);
        }
    }

    @Override
    public WhitesnakeRenderState createRenderState() {
        return new WhitesnakeRenderState();
    }

    @Override
    public void extractRenderState(
            WhitesnakeEntity entity,
            WhitesnakeRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.disguiseType = entity.getDisguiseType();
        renderState.disguiseChangedTick =
                entity.getDisguiseChangedTick();
    }

    @Override
    public void render(
            WhitesnakeEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        // The look goes on at the Stand's place as the Stand goes: WhitesnakeRecalledDisguiseEvents draws it, with no fade.
        if (entity.isDisguised()
                && entity.isUnsummoning()
                && !entity.isArmsOnlyMode()) {
            return;
        }
        WhitesnakeRenderState renderState =
                createRenderState(entity, partialTick);
        StandVisualContext visualContext =
                renderState.visualContext;
        if (renderState.disguiseType.isPresent()
                && visualContext != null
                && !entity.isArmsOnlyMode()
                && renderState.obstructionRenderMode == ObstructionRenderMode.NONE
                && renderDisguise(
                        entity,
                        renderState,
                        entityYaw,
                        partialTick,
                        poseStack,
                        bufferSource,
                        packedLight)) {
            return;
        }
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                bufferSource,
                packedLight);
    }

    private boolean renderDisguise(
            WhitesnakeEntity whitesnake,
            WhitesnakeRenderState renderState,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        StandVisualContext visualContext =
                renderState.visualContext;
        if (visualContext.effectiveAlpha() <= 0.0F
                || renderState.isInvisibleToPlayer
                        && !renderState.visibleForSpectator) {
            return true;
        }
        if (renderingDisguise) {
            return false;
        }

        ResourceLocation typeId =
                renderState.disguiseType.orElse(null);
        LivingEntity preview = previewOf(typeId);
        if (preview == null) {
            return false;
        }
        copyRenderPose(whitesnake, preview);

        float transformProgress = Mth.clamp(
                (whitesnake.tickCount + partialTick
                        - renderState.disguiseChangedTick)
                        / TRANSFORM_TICKS,
                0.05F,
                1.0F);
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        bufferSource,
                        visualContext.effectiveAlpha());
        RenderStateCrutches.Snapshot snapshot =
                preRender(renderState);
        poseStack.pushPose();
        renderingDisguise = true;
        try {
            poseStack.scale(
                    transformProgress,
                    transformProgress,
                    transformProgress);
            renderEntity(
                    preview,
                    entityYaw,
                    partialTick,
                    poseStack,
                    alphaBuffer,
                    packedLight);
            return true;
        }
        catch (RuntimeException | LinkageError exception) {
            rejectedRenderers.add(typeId);
            previewEntities.remove(typeId);
            LOGGER.warn(
                    "Whitesnake disguise renderer rejected entity type {}; "
                            + "falling back to the Stand renderer.",
                    typeId,
                    exception);
            return false;
        }
        finally {
            renderingDisguise = false;
            poseStack.popPose();
            postRender(snapshot);
        }
    }

    private void renderBesideUser(
            AbstractClientPlayer user,
            ResourceLocation typeId,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Vec3 camera,
            float partialTick) {
        float alpha = StandOpacityPolicy.apply(1.0F, user);
        LivingEntity preview = alpha > 0.0F ? previewOf(typeId) : null;
        if (preview == null) {
            return;
        }
        float yRot = user.getViewYRot(partialTick);
        Vec3 position = user.getPosition(partialTick).add(
                StandOffsetFromUser.relativeToAbsolute(
                        STAND_PLACE,
                        StandOffsetFromUser.Rotations.HEAD,
                        yRot,
                        user.getViewXRot(partialTick),
                        PlayerClientBroadcastedSettings
                                .getPlayerSettings(user),
                        true));
        copyRenderPose(user, preview);
        poseStack.pushPose();
        try {
            poseStack.translate(
                    position.x - camera.x,
                    position.y - camera.y,
                    position.z - camera.z);
            renderEntity(
                    preview,
                    yRot,
                    partialTick,
                    poseStack,
                    AlphaMultiBufferSource.wrap(bufferSource, alpha),
                    Minecraft.getInstance()
                            .getEntityRenderDispatcher()
                            .getPackedLightCoords(user, partialTick));
        }
        catch (RuntimeException | LinkageError exception) {
            rejectedRenderers.add(typeId);
            previewEntities.remove(typeId);
            LOGGER.warn(
                    "Whitesnake disguise renderer rejected entity type {}.",
                    typeId,
                    exception);
        }
        finally {
            poseStack.popPose();
        }
    }

    private LivingEntity previewOf(ResourceLocation typeId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null
                || typeId == null
                || rejectedRenderers.contains(typeId)) {
            return null;
        }
        if (previewLevel != level) {
            clearPreviewCache();
            previewLevel = level;
        }
        LivingEntity preview = previewEntities.get(typeId);
        if (preview == null) {
            EntityType<?> entityType =
                    BuiltInRegistries.ENTITY_TYPE
                            .getOptional(typeId)
                            .orElse(null);
            preview = DisguiseTypePolicy.createAllowedInstance(
                    entityType, level);
            if (preview == null) {
                rejectedRenderers.add(typeId);
                return null;
            }
            cachePreview(typeId, preview);
        }
        return preview;
    }

    private void clearPreviewCache() {
        for (LivingEntity preview : previewEntities.values()) {
            preview.remove(
                    net.minecraft.world.entity.Entity
                            .RemovalReason.DISCARDED);
        }
        previewEntities.clear();
        rejectedRenderers.clear();
    }

    private void cachePreview(
            ResourceLocation typeId, LivingEntity preview) {
        previewEntities.put(typeId, preview);
        if (previewEntities.size() <= MAX_PREVIEW_CACHE) {
            return;
        }
        var iterator = previewEntities.entrySet().iterator();
        if (iterator.hasNext()) {
            var eldest = iterator.next();
            eldest.getValue().remove(
                    net.minecraft.world.entity.Entity
                            .RemovalReason.DISCARDED);
            iterator.remove();
        }
    }

    private static void copyRenderPose(
            LivingEntity source, LivingEntity preview) {
        preview.setPos(
                source.getX(),
                source.getY(),
                source.getZ());
        preview.setYRot(source.getYRot());
        preview.setXRot(source.getXRot());
        preview.xRotO = source.xRotO;
        preview.yBodyRot = source.yBodyRot;
        preview.yBodyRotO = source.yBodyRotO;
        preview.yHeadRot = source.yHeadRot;
        preview.yHeadRotO = source.yHeadRotO;
        preview.tickCount = source.tickCount;
        preview.hurtTime = source.hurtTime;
        preview.deathTime = source.deathTime;
        preview.setPose(source.getPose());
        preview.setDeltaMovement(source.getDeltaMovement());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderEntity(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        EntityRenderer renderer = Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getRenderer(entity);
        renderer.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                bufferSource,
                packedLight);
    }
}
