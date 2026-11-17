package rotp.addons.khnum.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.khnum.disguise.DisguiseMode;
import rotp.addons.khnum.disguise.KhnumDisguiseState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;

public final class KhnumDisguiseLayer extends RenderLayer<
        AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>>
        implements FirstPersonModelLayer {
    private static final Map<ResourceLocation, EntityVisual>
            ENTITY_VISUALS = new HashMap<>();
    private static final Set<ResourceLocation> REJECTED_TYPES =
            new HashSet<>();
    private static ClientLevel cachedLevel;
    private static PlayerModel<AbstractClientPlayer>
            firstPersonNormalModel;
    private static PlayerModel<AbstractClientPlayer>
            firstPersonSlimModel;

    private final PlayerModel<AbstractClientPlayer> normalModel;
    private final PlayerModel<AbstractClientPlayer> slimModel;

    public KhnumDisguiseLayer(
            RenderLayerParent<
                    AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent,
            ModelPart normalRoot,
            ModelPart slimRoot) {
        super(parent);
        normalModel = new PlayerModel<>(normalRoot, false);
        slimModel = new PlayerModel<>(slimRoot, true);
        firstPersonNormalModel = normalModel;
        firstPersonSlimModel = slimModel;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        KhnumVisuals.RenderState state =
                KhnumVisuals.resolve(player, partialTick);
        // Mob disguises are drawn by renderEntityReplacement instead: a
        // layer's pose is already in the player's flipped model space, so a
        // whole mob renderer called here draws the mob upside down.
        if (state == null
                || state.disguise().mode()
                        != DisguiseMode.PLAYER) {
            return;
        }
        renderPlayer(
                state.disguise(),
                player,
                poseStack,
                AlphaMultiBufferSource.wrap(
                        buffer,
                        state.alpha()),
                packedLight,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch);
    }

    /**
     * Draws a mob disguise in place of the whole player render, with the
     * pose stack at the player's entity origin.
     */
    public static boolean renderEntityReplacement(
            AbstractClientPlayer player,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick) {
        KhnumVisuals.RenderState state =
                KhnumVisuals.resolve(player, partialTick);
        if (state == null
                || state.disguise().mode()
                        != DisguiseMode.ENTITY) {
            return false;
        }
        EntityVisual visual = entityVisual(
                state.disguise().entityId().orElse(null));
        if (visual == null) {
            return false;
        }
        renderEntity(
                visual,
                player,
                poseStack,
                AlphaMultiBufferSource.wrap(
                        buffer,
                        state.alpha()),
                packedLight,
                partialTick);
        return true;
    }

    @Override
    public void renderHandFirstPerson(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            LivingEntityRenderer<?, ?> entityRenderer,
            float partialTick) {
        if (entity instanceof AbstractClientPlayer player) {
            renderFirstPersonReplacement(
                    player,
                    side,
                    poseStack,
                    buffer,
                    light,
                    partialTick);
        }
    }

    public static boolean canRender(
            AbstractClientPlayer player, float partialTick) {
        KhnumVisuals.RenderState state =
                KhnumVisuals.resolve(player, partialTick);
        if (state == null
                || !state.disguise().isDisguised()) {
            return false;
        }
        return state.disguise().mode()
                        == DisguiseMode.PLAYER
                ? state.disguise().playerId().isPresent()
                : entityVisual(
                        state.disguise().entityId()
                                .orElse(null)) != null;
    }

    public static boolean renderFirstPersonReplacement(
            AbstractClientPlayer player,
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            float partialTick) {
        KhnumVisuals.RenderState state =
                KhnumVisuals.resolve(player, partialTick);
        if (state == null
                || !state.disguise().isDisguised()) {
            return false;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        state.alpha());
        if (state.disguise().mode()
                == DisguiseMode.PLAYER) {
            PlayerSkin skin = playerSkin(state.disguise());
            PlayerModel<AbstractClientPlayer> model =
                    skin != null
                                    && skin.model()
                                            == PlayerSkin.Model.SLIM
                            ? firstPersonSlimModel
                            : firstPersonNormalModel;
            if (skin == null || model == null) {
                return false;
            }
            renderFirstPersonArm(
                    model,
                    player,
                    side,
                    poseStack,
                    alphaBuffer,
                    light,
                    skin.texture(),
                    partialTick);
            return true;
        }

        EntityVisual visual = entityVisual(
                state.disguise().entityId().orElse(null));
        if (visual == null) {
            return false;
        }
        syncDummy(visual.dummy(), player);
        if (visual.model() instanceof HumanoidModel<?> humanoid) {
            renderFirstPersonArm(
                    humanoid,
                    visual.dummy(),
                    side,
                    poseStack,
                    alphaBuffer,
                    light,
                    visual.texture(),
                    partialTick);
        }
        return true;
    }

    public static void clearCache() {
        for (EntityVisual visual : ENTITY_VISUALS.values()) {
            visual.dummy().remove(Entity.RemovalReason.DISCARDED);
        }
        ENTITY_VISUALS.clear();
        REJECTED_TYPES.clear();
        cachedLevel = null;
    }

    private void renderPlayer(
            KhnumDisguiseState disguise,
            AbstractClientPlayer wearer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        PlayerSkin skin = playerSkin(disguise);
        if (skin == null) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model =
                skin.model() == PlayerSkin.Model.SLIM
                        ? slimModel
                        : normalModel;
        getParentModel().copyPropertiesTo(model);
        model.setAllVisible(true);
        model.setupAnim(
                wearer,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch);
        VertexConsumer vertices = buffer.getBuffer(
                ModRenderTypes.standTranslucentCull(
                        skin.texture()));
        model.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        wearer, 0.0F));
    }

    private static PlayerSkin playerSkin(
            KhnumDisguiseState disguise) {
        UUID uuid = disguise.playerId().orElse(null);
        if (uuid == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        var info = connection != null
                ? connection.getPlayerInfo(uuid)
                : null;
        return info != null
                ? info.getSkin()
                : minecraft.getSkinManager().getInsecureSkin(
                        new GameProfile(
                                uuid,
                                disguise.playerName()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderEntity(
            EntityVisual visual,
            AbstractClientPlayer wearer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick) {
        LivingEntity dummy = visual.dummy();
        syncDummy(dummy, wearer);
        renderNative(
                visual.renderer(),
                dummy,
                wearer,
                poseStack,
                buffer,
                packedLight,
                partialTick);
    }

    // The renderer interpolates between the previous and current values, and a
    // dummy outside the level never ticks: an xRotO left at 0 drew the head's
    // pitch from level up to the look again on every tick.
    private static void syncDummy(
            LivingEntity dummy,
            AbstractClientPlayer wearer) {
        dummy.setPose(wearer.getPose());
        dummy.setShiftKeyDown(wearer.isShiftKeyDown());
        dummy.setOnGround(wearer.onGround());
        dummy.setPos(wearer.getX(), wearer.getY(), wearer.getZ());
        dummy.xo = wearer.xo;
        dummy.yo = wearer.yo;
        dummy.zo = wearer.zo;
        dummy.xOld = wearer.xOld;
        dummy.yOld = wearer.yOld;
        dummy.zOld = wearer.zOld;
        dummy.setYRot(wearer.getYRot());
        dummy.yRotO = wearer.yRotO;
        dummy.setXRot(wearer.getXRot());
        dummy.xRotO = wearer.xRotO;
        dummy.yBodyRot = wearer.yBodyRot;
        dummy.yBodyRotO = wearer.yBodyRotO;
        dummy.yHeadRot = wearer.yHeadRot;
        dummy.yHeadRotO = wearer.yHeadRotO;
        dummy.swinging = wearer.swinging;
        dummy.swingingArm = wearer.swingingArm;
        dummy.swingTime = wearer.swingTime;
        dummy.hurtTime = wearer.hurtTime;
        dummy.hurtDuration = wearer.hurtDuration;
        dummy.oAttackAnim = wearer.oAttackAnim;
        dummy.attackAnim = wearer.attackAnim;
        copyWalkAnimation(wearer.walkAnimation, dummy.walkAnimation);
        dummy.tickCount = wearer.tickCount;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            try {
                dummy.setItemSlot(
                        slot, wearer.getItemBySlot(slot).copy());
            }
            catch (RuntimeException ignored) {
                // Some modded living entities reject unsupported slots.
            }
        }
        if (wearer.isUsingItem()) {
            if (!dummy.isUsingItem()
                    || dummy.getUsedItemHand()
                            != wearer.getUsedItemHand()) {
                dummy.startUsingItem(wearer.getUsedItemHand());
            }
        }
        else if (dummy.isUsingItem()) {
            dummy.stopUsingItem();
        }
    }

    // WalkAnimationState only exposes setSpeed and update; two updates land
    // its private speedOld, speed and position on the wearer's values.
    private static void copyWalkAnimation(
            WalkAnimationState from, WalkAnimationState to) {
        float speedOld = from.speed(0.0F);
        float speed = from.speed();
        to.update(from.position() - speed - to.position(), 1.0F);
        to.setSpeed(speedOld);
        to.update(speed, 1.0F);
    }

    private static EntityVisual entityVisual(
            ResourceLocation typeId) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null
                || typeId == null
                || REJECTED_TYPES.contains(typeId)) {
            return null;
        }
        if (cachedLevel != level) {
            clearCache();
            cachedLevel = level;
        }
        EntityVisual cached = ENTITY_VISUALS.get(typeId);
        if (cached != null) {
            return cached;
        }
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                    .getOptional(typeId).orElse(null);
            Entity created = type != null ? type.create(level) : null;
            if (!(created instanceof LivingEntity living)) {
                if (created != null) {
                    created.remove(Entity.RemovalReason.DISCARDED);
                }
                REJECTED_TYPES.add(typeId);
                return null;
            }
            EntityRenderer<?> renderer = minecraft
                    .getEntityRenderDispatcher()
                    .getRenderer(living);
            if (!(renderer
                    instanceof LivingEntityRenderer<?, ?>
                            livingRenderer)) {
                living.remove(Entity.RemovalReason.DISCARDED);
                REJECTED_TYPES.add(typeId);
                return null;
            }
            ResourceLocation texture = texture(
                    renderer, living);
            EntityVisual visual = new EntityVisual(
                    living,
                    renderer,
                    livingRenderer.getModel(),
                    texture);
            ENTITY_VISUALS.put(typeId, visual);
            return visual;
        }
        catch (RuntimeException | LinkageError ignored) {
            REJECTED_TYPES.add(typeId);
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceLocation texture(
            EntityRenderer renderer, LivingEntity entity) {
        return renderer.getTextureLocation(entity);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderNative(
            EntityRenderer renderer,
            LivingEntity dummy,
            AbstractClientPlayer wearer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick) {
        poseStack.pushPose();
        try {
            renderer.render(
                    dummy,
                    wearer.getYRot(),
                    partialTick,
                    poseStack,
                    buffer,
                    packedLight);
        }
        finally {
            poseStack.popPose();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderFirstPersonArm(
            HumanoidModel<?> model,
            LivingEntity entity,
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            ResourceLocation texture,
            float partialTick) {
        FirstPersonModelLayer.setupForFirstPersonRender(
                (HumanoidModel) model, entity, partialTick);
        VertexConsumer vertices = buffer.getBuffer(
                ModRenderTypes.standTranslucentCull(texture));
        FirstPersonModelLayer.renderArmAndOuter(
                model,
                side,
                poseStack,
                vertices,
                light,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF,
                FirstPersonModelLayer.isRipplesAnimPlaying(model));
    }

    private record EntityVisual(
            LivingEntity dummy,
            EntityRenderer<?> renderer,
            EntityModel<?> model,
            ResourceLocation texture) {}
}
