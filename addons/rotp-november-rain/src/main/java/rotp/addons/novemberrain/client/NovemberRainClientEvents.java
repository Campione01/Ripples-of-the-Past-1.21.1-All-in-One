package rotp.addons.novemberrain.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.novemberrain.NovemberRainAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = NovemberRainAddon.MOD_ID, value = Dist.CLIENT)
public final class NovemberRainClientEvents {
    private NovemberRainClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                NovemberRainAddon.NOVEMBER_RAIN_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                NovemberRainAddon.RAIN_DROP.get(),
                RainDropRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RainDropModel.LAYER_LOCATION, RainDropModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, NovemberRainAddon.id("cut_eyelid"), new CutEyelidOverlay());
    }
}
