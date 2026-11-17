package rotp.addons.whitesnake.client;

import com.mojang.blaze3d.vertex.PoseStack;

import rotp.core.client.ClientGlobals;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disguise.WhitesnakeDisguiseState;
import rotp.addons.whitesnake.entity.WhitesnakeEntity;
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
 * A disguise with no Stand out to carry it goes on drawing at the Stand's place, on every client that tracks the user.
 */
@EventBusSubscriber(
        modid = WhitesnakeAddon.MOD_ID,
        value = Dist.CLIENT)
public final class WhitesnakeRecalledDisguiseEvents {
    private WhitesnakeRecalledDisguiseEvents() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || !ClientGlobals.canSeeStands) {
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
            WhitesnakeDisguiseState disguise =
                    WhitesnakeDisguiseState.of(player);
            if (disguise.isDisguised()
                    && !player.isSpectator()
                    && !aStandCarriesIt(player)) {
                WhitesnakeRenderer.renderWithoutStand(
                        player,
                        disguise.type().orElseThrow(),
                        poseStack,
                        buffer,
                        camera,
                        partialTick);
            }
        }
    }

    private static boolean aStandCarriesIt(AbstractClientPlayer player) {
        StandPower power = StandPower.get(player);
        return power != null
                && power.getSummonedStandEntity()
                        instanceof WhitesnakeEntity whitesnake
                && !whitesnake.isArmsOnlyMode()
                && !whitesnake.isUnsummoning();
    }
}
