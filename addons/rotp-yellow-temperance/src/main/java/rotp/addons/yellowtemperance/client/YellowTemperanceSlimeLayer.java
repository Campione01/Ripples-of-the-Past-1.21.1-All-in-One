package rotp.addons.yellowtemperance.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.YellowTemperanceConfig;
import rotp.addons.yellowtemperance.ability.YellowTemperanceBlockAbility;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * The manga's slime on the user, while the Stand is out. A choice in the module's settings, off by default: 1.16 drew
 * nothing on the user, and a player found the first coat of this port ugly.
 */
public final class YellowTemperanceSlimeLayer<T extends LivingEntity, M extends HumanoidModel<T>>
        extends RenderLayer<T, M> implements FirstPersonModelLayer {
    private static final int TICKS_PER_FRAME = 5;
    private static final ResourceLocation[] FRAMES = new ResourceLocation[6];
    static {
        for (int i = 0; i < FRAMES.length; i++) {
            FRAMES[i] = YellowTemperanceAddon.id("textures/entity/manga_slime/frame_" + i + ".png");
        }
    }

    private final PlayerModel<T> model;

    public YellowTemperanceSlimeLayer(RenderLayerParent<T, M> parent, PlayerModel<T> model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        StandVisualContext context = visible(entity, partialTick);
        if (context == null) {
            return;
        }
        // the user's own pose as it is drawn this frame, a Stand user's swing included
        M parent = getParentModel();
        parent.copyPropertiesTo(model);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
        // the head's inner cube carries only the thin film, which hangs off the face and reads as grey strips against the
        // sky; the goo on the outer cube is the slime there
        model.head.visible = false;
        model.hat.visible = parent.head.visible;
        model.body.visible = parent.body.visible;
        model.jacket.visible = parent.body.visible;
        model.rightArm.visible = parent.rightArm.visible;
        model.rightSleeve.visible = parent.rightArm.visible;
        model.leftArm.visible = parent.leftArm.visible;
        model.leftSleeve.visible = parent.leftArm.visible;
        model.rightLeg.visible = parent.rightLeg.visible;
        model.rightPants.visible = parent.rightLeg.visible;
        model.leftLeg.visible = parent.leftLeg.visible;
        model.leftPants.visible = parent.leftLeg.visible;
        model.renderToBuffer(
                poseStack,
                vertices(buffer, context, ageInTicks),
                packedLight,
                LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
                0xFFFFFFFF);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void renderHandFirstPerson(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            LivingEntityRenderer<?, ?> entityRenderer,
            float partialTick) {
        StandVisualContext context = visible(entity, partialTick);
        // the guard's arms are the Stand's: the hands stay bare under them, as 1.16's unused layer said
        if (context == null || isGuarding(entity)) {
            return;
        }
        model.setAllVisible(true);
        HumanoidModel<?> humanoid = model;
        FirstPersonModelLayer.setupForFirstPersonRender(
                (HumanoidModel<LivingEntity>) humanoid, entity, partialTick);
        FirstPersonModelLayer.renderArmAndOuter(
                model,
                side,
                poseStack,
                vertices(buffer, context, entity.tickCount + partialTick),
                light,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF,
                FirstPersonModelLayer.isRipplesAnimPlaying(model));
    }

    private static VertexConsumer vertices(MultiBufferSource buffer, StandVisualContext context, float age) {
        ResourceLocation texture = FRAMES[Math.floorMod((int) (age / TICKS_PER_FRAME), FRAMES.length)];
        // culled: the inside of the far faces would show through the film's empty rim as a pale frame against the sky
        return AlphaMultiBufferSource.wrap(buffer, context.effectiveAlpha())
                .getBuffer(ModRenderTypes.standTranslucentCull(texture));
    }

    private static StandVisualContext visible(LivingEntity user, float partialTick) {
        if (!YellowTemperanceConfig.SHOW_MANGA_SLIME.get()
                || !ClientGlobals.canSeeStands
                || user.isInvisible()
                || user.isSpectator()) {
            return null;
        }
        YellowTemperanceControllerEntity controller = YellowTemperanceAddon.controller(user);
        if (controller == null || controller.clientStuff == null || controller.isDisguised()) {
            return null;
        }
        StandVisualContext context = StandVisualContext.world(
                controller, controller.clientStuff.getAlpha(controller, partialTick));
        return context.effectiveAlpha() > 0.0F ? context : null;
    }

    private static boolean isGuarding(LivingEntity user) {
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(user, PowerClass.STAND);
        return action != null
                && action.ability instanceof YellowTemperanceBlockAbility
                && !action.isOver()
                && action.getPhase() != ActionPhase.RECOVERY;
    }
}
