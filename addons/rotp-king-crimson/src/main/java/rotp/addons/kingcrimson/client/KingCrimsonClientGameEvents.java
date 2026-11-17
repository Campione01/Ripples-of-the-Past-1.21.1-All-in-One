package rotp.addons.kingcrimson.client;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.epitaph.EpitaphClientSession;
import rotp.addons.kingcrimson.client.epitaph.EpitaphNotice;
import rotp.addons.kingcrimson.client.epitaph.EpitaphRemotePlayer;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterClient;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterDiagnostics;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterFinalProbe;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterVoidDepth;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterVoidTexture;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.core.api.client.render.ClientRenderCompatibility;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.util.TriState;

@EventBusSubscriber(
        modid = KingCrimsonAddon.MOD_ID,
        value = Dist.CLIENT)
public final class KingCrimsonClientGameEvents {
    private KingCrimsonClientGameEvents() {}

    // The phantom of a player bears no name: the one it stands for stands nearby.
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof EpitaphRemotePlayer) {
            event.setCanRender(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        EpitaphPostEffect.syncFromLocalPlayer();
        EpitaphClientSession.tick();
        KingCrimsonTemporalClientState.sync();
        KingCrimsonTemporalPostEffect.sync();
        KingCrimsonTemporalClientState.syncDecoyStandVisibility();
        KingCrimsonShiftedImagesClient.tick();
        KingCrimsonEraseWitnessView.tick();
        KingCrimsonBloodSplashOverlay.tick();
        KingCrimsonEraseOutsideView.tick();
        KingCrimsonShatterClient.tick();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        KingCrimsonShatterClient.onRenderLevelStage(event);
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            KingCrimsonAfterimageHalo.afterLevel(event);
            KingCrimsonTimeEraseTerrainOutline.afterLevel(event);
            KingCrimsonShatterDiagnostics.afterLevel();
            if (KingCrimsonShatterFinalProbe.ENABLED) {
                if (KingCrimsonShatterClient.drewIrisVoid()) {
                    var window = Minecraft.getInstance().getWindow();
                    // The isolated probe profile pins the pack's occupied scale to 1/3.
                    KingCrimsonShatterFinalProbe.capture(window.getWidth() / 3, window.getHeight() / 3);
                } else {
                    KingCrimsonShatterFinalProbe.close();
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderFrameStart(RenderFrameEvent.Pre event) {
        if (KingCrimsonShatterFinalProbe.ENABLED) KingCrimsonShatterFinalProbe.beginFrame();
        KingCrimsonShatterClient.onRenderFrameStart();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFrameEnd(RenderFrameEvent.Post event) {
        KingCrimsonShatterClient.onRenderFrameEnd();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (KingCrimsonShatterFinalProbe.ENABLED) KingCrimsonShatterFinalProbe.apply();
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        KingCrimsonTimeEraseCameraShake.onComputeCameraAngles(event);
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        KingCrimsonShiftedImagesClient.onRenderLiving(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderLivingForWitness(RenderLivingEvent.Pre<?, ?> event) {
        KingCrimsonEraseWitnessView.onRenderLiving(event);
    }

    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        if (KingCrimsonTemporalClientState.localTimeErase()
                && ClientRenderCompatibility.snapshot().irisShaderPackInUse()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        KingCrimsonTemporalState.resetEraseCount();
        KingCrimsonShiftedImagesClient.clear();
        EpitaphClientSession.end();
        EpitaphNotice.clear();
        KingCrimsonBloodSplashOverlay.clear();
        KingCrimsonEraseOutsideView.clear();
        KingCrimsonShatterClient.onLogout();
        KingCrimsonAfterimageHalo.close();
        KingCrimsonTimeEraseTerrainOutline.close();
        KingCrimsonShatterVoidDepth.close();
        KingCrimsonShatterVoidTexture.close();
        if (KingCrimsonShatterFinalProbe.ENABLED) KingCrimsonShatterFinalProbe.close();
    }
}
