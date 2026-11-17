package rotp.addons.yellowtemperance.client;

import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.disguise.YellowTemperanceDisguiseState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The fake Stand of the Stand disguise, which every client tracking its user must see, the user's own client in first
 * person too, with Yellow Temperance out or recalled: no entity is left to draw it, and a layer of the user's model
 * would not run in first person.
 */
@EventBusSubscriber(
        modid = YellowTemperanceAddon.MOD_ID,
        value = Dist.CLIENT)
public final class YellowTemperanceBesideStandEvents {
    private YellowTemperanceBesideStandEvents() {}

    @SubscribeEvent
    public static void renderBesideStands(RenderLevelStageEvent event) {
        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float partialTick =
                event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource buffer = minecraft.renderBuffers().bufferSource();
        for (AbstractClientPlayer player : level.players()) {
            if (YellowTemperanceDisguiseState.of(player)
                    .besideStand().isPresent()) {
                YellowTemperanceDisguiseLayer.renderBesideStand(
                        player, poseStack, buffer, camera, partialTick);
            }
        }
    }
}
