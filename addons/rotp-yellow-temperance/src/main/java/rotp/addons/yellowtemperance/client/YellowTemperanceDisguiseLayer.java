package rotp.addons.yellowtemperance.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityanim.PreFrameEntityAnimCalc;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimatedEntity;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.yellowtemperance.YellowTemperanceConfig;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.disguise.YellowTemperanceDisguiseState;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.phys.Vec3;

public final class YellowTemperanceDisguiseLayer extends RenderLayer<
        AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>> {
    private static final Map<VisualKey, LivingEntity> DUMMIES =
            new HashMap<>();
    private static final AnimFramePose WEARER_POSE = new AnimFramePose();
    // Past any summon clip, which 1.16's disguised Stand never played: it only grew in.
    private static final int BESIDE_DUMMY_AGE = 1000;
    private static ClientLevel cachedLevel;

    private final PlayerModel<AbstractClientPlayer> normalModel;
    private final PlayerModel<AbstractClientPlayer> slimModel;

    public YellowTemperanceDisguiseLayer(
            RenderLayerParent<
                    AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent,
            ModelPart normalRoot,
            ModelPart slimRoot) {
        super(parent);
        normalModel = new PlayerModel<>(normalRoot, false);
        slimModel = new PlayerModel<>(slimRoot, true);
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
        YellowTemperanceVisuals.RenderState state =
                YellowTemperanceVisuals.resolve(
                        player, partialTick);
        if (state == null
                || state.disguise().mode()
                        != DisguiseMode.PLAYER) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        state.alpha());
        if (state.disguise().mode()
                == DisguiseMode.PLAYER) {
            renderPlayer(
                    state.disguise(),
                    player,
                    poseStack,
                    alphaBuffer,
                    packedLight,
                    limbSwing,
                    limbSwingAmount,
                    ageInTicks,
                    netHeadYaw,
                    headPitch);
            return;
        }
    }

    // Full entity renderers need the entity frame, not a player layer's model frame.
    static void renderEntityDisguise(
            AbstractClientPlayer player,
            PlayerModel<?> userModel,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick) {
        if (player.isSpectator()) {
            return;
        }
        YellowTemperanceVisuals.RenderState state =
                YellowTemperanceVisuals.resolve(player, partialTick);
        if (state == null
                || !state.disguise().isDisguised()
                || state.disguise().mode() == DisguiseMode.PLAYER) {
            return;
        }
        LivingEntity dummy = dummy(state.disguise().mode(),
                state.disguise().disguiseId().orElse(null));
        if (dummy != null) {
            renderEntity(dummy, state.disguise(), player, userModel, poseStack,
                    AlphaMultiBufferSource.wrap(buffer, state.alpha()),
                    packedLight, partialTick);
        }
    }

    /**
     * 1.16 YellowTemperanceRenderer: the Stand entity, at its usual place behind the user, takes the chosen Stand's
     * model in its idle pose and looks where the user looks; the user is drawn as he is. No Stand entity is needed for
     * it, so it goes on with Yellow Temperance recalled. The pose stack is at the camera's origin, as the level's
     * entities get it.
     */
    static void renderBesideStand(
            AbstractClientPlayer user,
            PoseStack poseStack,
            MultiBufferSource buffer,
            Vec3 camera,
            float partialTick) {
        if (!ClientGlobals.canSeeStands) {
            return;
        }
        YellowTemperanceVisuals.RenderState state =
                YellowTemperanceVisuals.resolve(user, partialTick);
        if (state == null
                || !(dummy(DisguiseMode.STAND, state.disguise().besideStand().orElse(null))
                        instanceof StandEntity dummy)) {
            return;
        }
        float yRot = user.getViewYRot(partialTick);
        // the same place as the Stand's box, which can be struck there
        Vec3 position = YellowTemperanceControllerEntity.besidePosition(user, partialTick);
        placeBeside(dummy, user, position);
        AnimFramePose pose = PreFrameEntityAnimCalc.getLivingPose(dummy, partialTick, true);
        AnimatedEntity animated = (AnimatedEntity) dummy;
        if (pose == null) {
            animated.jojo_ripples$setModelPose(AnimatedEntity.PoseType.UNMODIFIED, null);
        }
        animated.jojo_ripples$setModelPose(AnimatedEntity.PoseType.FINAL, pose);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        float scale = standTransition(
                user.tickCount + partialTick - state.disguise().besideShownTick());
        poseStack.pushPose();
        try {
            poseStack.translate(
                    position.x - camera.x,
                    position.y - camera.y,
                    position.z - camera.z);
            poseStack.scale(scale, scale, scale);
            EntityRenderer<? super LivingEntity> renderer = dispatcher.getRenderer(dummy);
            renderer.render(
                    dummy,
                    yRot,
                    partialTick,
                    poseStack,
                    AlphaMultiBufferSource.wrap(buffer, state.alpha()),
                    dispatcher.getPackedLightCoords(dummy, partialTick));
        }
        finally {
            poseStack.popPose();
        }
    }

    // No previous position that differs: 1.16 drew this Stand with no walk or motion of its own.
    private static void placeBeside(
            StandEntity dummy, LivingEntity user, Vec3 position) {
        dummy.setPos(position.x, position.y, position.z);
        dummy.xo = position.x;
        dummy.yo = position.y;
        dummy.zo = position.z;
        dummy.xOld = position.x;
        dummy.yOld = position.y;
        dummy.zOld = position.z;
        dummy.setYRot(user.getYRot());
        dummy.yRotO = user.yRotO;
        dummy.setXRot(user.getXRot());
        dummy.xRotO = user.xRotO;
        dummy.yBodyRot = user.yBodyRot;
        dummy.yBodyRotO = user.yBodyRotO;
        dummy.yHeadRot = user.yHeadRot;
        dummy.yHeadRotO = user.yHeadRotO;
        dummy.tickCount = BESIDE_DUMMY_AGE + user.tickCount;
    }

    public static boolean canRender(
            AbstractClientPlayer player, float partialTick) {
        YellowTemperanceVisuals.RenderState state =
                YellowTemperanceVisuals.resolve(
                        player, partialTick);
        if (state == null
                || !state.disguise().isDisguised()) {
            return false;
        }
        return state.disguise().mode()
                        == DisguiseMode.PLAYER
                ? state.disguise().playerId().isPresent()
                : dummy(state.disguise().mode(),
                        state.disguise().disguiseId().orElse(null)) != null;
    }

    public static void clearCache() {
        for (LivingEntity entity : DUMMIES.values()) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
        DUMMIES.clear();
        cachedLevel = null;
    }

    private void renderPlayer(
            YellowTemperanceDisguiseState disguise,
            AbstractClientPlayer wearer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        UUID uuid = disguise.playerId().orElse(null);
        if (uuid == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        var info = connection != null
                ? connection.getPlayerInfo(uuid)
                : null;
        PlayerSkin skin = info != null
                ? info.getSkin()
                : minecraft.getSkinManager().getInsecureSkin(
                        new GameProfile(
                                uuid,
                                disguise.playerName()));
        PlayerModel<AbstractClientPlayer> model =
                skin.model() == PlayerSkin.Model.SLIM
                        ? slimModel
                        : normalModel;
        poseStack.pushPose();
        try {
            float scale = transitionScale(disguise, ageInTicks);
            poseStack.scale(scale, scale, scale);
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
        finally {
            poseStack.popPose();
        }
    }

    private static void renderEntity(
            LivingEntity dummy,
            YellowTemperanceDisguiseState disguise,
            AbstractClientPlayer wearer,
            PlayerModel<?> userModel,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick) {
        copyFrame(wearer, dummy);
        if (dummy instanceof StandEntity) {
            // The dummy is outside the level, so no Stand clip is ever posed on it;
            // it takes the wearer's posed head and limbs instead (1.16 CopyBipedUserPose).
            ((AnimatedEntity) dummy).jojo_ripples$setModelPose(
                    AnimatedEntity.PoseType.FINAL, wearerPose(userModel));
        }
        EntityRenderDispatcher dispatcher =
                Minecraft.getInstance()
                        .getEntityRenderDispatcher();
        poseStack.pushPose();
        try {
            float scale = transitionScale(
                    disguise,
                    wearer.tickCount + partialTick);
            poseStack.scale(scale, scale, scale);
            EntityRenderer<? super LivingEntity> renderer =
                    dispatcher.getRenderer(dummy);
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

    // The renderer interpolates between the previous and current values, so the
    // previous ones must be the wearer's as well (1.16 synchronizeEntityState).
    private static void copyFrame(
            AbstractClientPlayer wearer, LivingEntity dummy) {
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
        dummy.tickCount = wearer.tickCount;
        dummy.attackAnim = wearer.attackAnim;
        dummy.oAttackAnim = wearer.oAttackAnim;
        dummy.swinging = wearer.swinging;
        dummy.swingingArm = wearer.swingingArm;
        dummy.swingTime = wearer.swingTime;
        dummy.hurtTime = wearer.hurtTime;
        dummy.hurtDuration = wearer.hurtDuration;
        copyWalkAnimation(wearer.walkAnimation, dummy.walkAnimation);
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

    private static AnimFramePose wearerPose(PlayerModel<?> model) {
        AnimFramePose pose = WEARER_POSE.clear();
        copyRotation(pose, "head", model.head);
        copyRotation(pose, "left_arm", model.leftArm);
        copyRotation(pose, "right_arm", model.rightArm);
        copyRotation(pose, "left_leg", model.leftLeg);
        copyRotation(pose, "right_leg", model.rightLeg);
        return pose;
    }

    private static void copyRotation(
            AnimFramePose pose, String bone, ModelPart from) {
        pose.getForModelPart(bone).rotationOffset.set(
                from.xRot, from.yRot, from.zRot);
    }

    private static float transitionScale(
            YellowTemperanceDisguiseState disguise,
            float ageInTicks) {
        return standTransition(
                ageInTicks - disguise.changedTick());
    }

    private static float standTransition(float ticksSinceChange) {
        double transitionTicks = YellowTemperanceConfig
                .DISGUISE_STAND_TRANSITION_TICKS.get();
        return Mth.clamp(
                (float) (ticksSinceChange / transitionTicks),
                0.0F,
                1.0F);
    }

    private static LivingEntity dummy(
            DisguiseMode mode, ResourceLocation id) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || id == null
                || mode == DisguiseMode.NONE
                || mode == DisguiseMode.PLAYER) {
            return null;
        }
        if (cachedLevel != level) {
            clearCache();
            cachedLevel = level;
        }
        VisualKey key = new VisualKey(mode, id);
        LivingEntity cached = DUMMIES.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            LivingEntity created = switch (mode) {
                case ENTITY -> createEntity(level, id);
                case STAND, STAND_SELF -> createStand(level, id);
                default -> null;
            };
            if (created != null) {
                DUMMIES.put(key, created);
            }
            return created;
        }
        catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static LivingEntity createEntity(
            ClientLevel level, ResourceLocation id) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                .getOptional(id).orElse(null);
        Entity entity = type != null ? type.create(level) : null;
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    private static LivingEntity createStand(
            ClientLevel level, ResourceLocation id) {
        Object standType = JojoRegistries.DEFAULT_STANDS_REG
                .getOptional(id).orElse(null);
        if (!(standType
                instanceof EntityStandType entityStandType)) {
            return null;
        }
        Entity entity = entityStandType.getEntityType().create(level);
        if (!(entity instanceof StandEntity stand)) {
            return null;
        }
        stand.withStandType(entityStandType);
        for (int tick = 0; tick < 80; tick++) {
            stand.tick();
        }
        return stand;
    }

    private record VisualKey(
            DisguiseMode mode, ResourceLocation id) {}
}
