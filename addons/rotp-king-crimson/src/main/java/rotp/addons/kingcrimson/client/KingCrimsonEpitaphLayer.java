package rotp.addons.kingcrimson.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandOpacityPolicy;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.config.client.ClientModSettings;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.epitaph.EpitaphClientSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public final class KingCrimsonEpitaphLayer extends RenderLayer<
        AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>>
        implements FirstPersonModelLayer {
    private static final ResourceLocation TEXTURE =
            KingCrimsonAddon.id("textures/entity/epitaph.png");

    public KingCrimsonEpitaphLayer(
            RenderLayerParent<
                    AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
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
        StandVisualContext context =
                visualContext(player, partialTick);
        if (context == null) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer, context.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        TEXTURE, context.classicOutline()));
        getParentModel().renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        player, 0.0F),
                0xFFFFFFFF);
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
        if (!(entity instanceof AbstractClientPlayer player)
                || !(entityRenderer.getModel()
                        instanceof HumanoidModel<?> model)) {
            return;
        }
        StandVisualContext context =
                visualContext(player, partialTick);
        if (context == null) {
            return;
        }
        FirstPersonModelLayer.defaultRender(
                side,
                poseStack,
                AlphaMultiBufferSource.wrap(
                        buffer, context.effectiveAlpha()),
                light,
                entity,
                entityRenderer,
                model,
                TEXTURE,
                partialTick);
    }

    private static StandVisualContext visualContext(
            AbstractClientPlayer player,
            float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientGlobals.canSeeStands
                || player.isInvisible()
                || player.isSpectator()
                || minecraft.level == null
                // Epitaph shows on the one who foresees: on his own screen while his prediction runs, on the screens
                // of the others while he is marked (a player his prediction is aimed at is never told of the mark).
                || !(player == minecraft.player ? EpitaphClientSession.current() != null
                        : player.hasData(KingCrimsonAddon.EPITAPH_FORESEEING))) {
            return null;
        }
        StandPower power = StandPower.get(player);
        if (power == null
                || power.getPowerType()
                        != KingCrimsonAddon.KING_CRIMSON.get()) {
            return null;
        }
        if (!(power.getSummonedStandEntity()
                instanceof StandEntity controller)) {
            return withoutStand(player);
        }
        if (controller.clientStuff == null) {
            return null;
        }
        float lifecycleAlpha = controller.clientStuff.getAlpha(
                controller, partialTick);
        StandVisualContext context =
                StandVisualContext.world(
                        controller, lifecycleAlpha);
        return context.effectiveAlpha() > 0.0F
                ? context
                : null;
    }

    // The vision goes on with the Stand recalled: whole, at the opacity of its user's Stand.
    private static StandVisualContext withoutStand(
            AbstractClientPlayer player) {
        ClientModSettings.Settings settings =
                ClientModSettings.getSettingsReadOnly();
        float alpha = StandOpacityPolicy.apply(1.0F, player);
        return alpha > 0.0F
                ? new StandVisualContext(
                        1.0F,
                        StandOpacityPolicy.opacityMultiplierForUser(player),
                        alpha,
                        StandOpacityPolicy.isOwnStandUser(player),
                        settings.classicStandObstruction,
                        settings.classicStandObstruction
                                && settings.standOutline)
                : null;
    }
}
