package rotp.addons.kingcrimson.client;

import rotp.core.adventure.npc.client.CharacterMobRenderer;

import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;

public final class KingCrimsonTimeEraseDecoyRenderer
        extends CharacterMobRenderer<
                KingCrimsonTimeEraseDecoyEntity> {
    public KingCrimsonTimeEraseDecoyRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    // T1: whoever sees in a stopped world does not see the decoy of an erase that is frozen in it
    @Override
    public boolean shouldRender(
            KingCrimsonTimeEraseDecoyEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        return !KingCrimsonTemporalClientState.hidesDecoyFromLocalViewer(entity)
                && super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ);
    }

    @Override
    public void render(
            KingCrimsonTimeEraseDecoyEntity entity,
            float entityYaw,
            float partialTick,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffer,
            int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCameraEntity() == entity.getOwner()
                && entity.getBoundingBox().inflate(0.1D)
                        .contains(minecraft.gameRenderer.getMainCamera().getPosition())) {
            return;
        }
        applyPlayerLook(regularPlayerModel, entity);
        applyPlayerLook(slimPlayerModel, entity);
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                buffer,
                packedLight);
    }

    // As PlayerRenderer draws a crouching player.
    @Override
    public Vec3 getRenderOffset(
            KingCrimsonTimeEraseDecoyEntity entity,
            float partialTick) {
        return entity.isCrouching()
                ? new Vec3(0.0D, entity.getScale() * -2.0F / 16.0F, 0.0D)
                : super.getRenderOffset(entity, partialTick);
    }

    private static void applyPlayerLook(
            EntityModel<KingCrimsonTimeEraseDecoyEntity> model,
            KingCrimsonTimeEraseDecoyEntity entity) {
        if (!(model instanceof PlayerModel<?> playerModel)) {
            return;
        }
        playerModel.crouching = entity.isCrouching();
        playerModel.hat.visible =
                entity.isSkinPartShown(PlayerModelPart.HAT);
        playerModel.jacket.visible =
                entity.isSkinPartShown(PlayerModelPart.JACKET);
        playerModel.leftSleeve.visible =
                entity.isSkinPartShown(
                        PlayerModelPart.LEFT_SLEEVE);
        playerModel.rightSleeve.visible =
                entity.isSkinPartShown(
                        PlayerModelPart.RIGHT_SLEEVE);
        playerModel.leftPants.visible =
                entity.isSkinPartShown(
                        PlayerModelPart.LEFT_PANTS_LEG);
        playerModel.rightPants.visible =
                entity.isSkinPartShown(
                        PlayerModelPart.RIGHT_PANTS_LEG);
    }
}
