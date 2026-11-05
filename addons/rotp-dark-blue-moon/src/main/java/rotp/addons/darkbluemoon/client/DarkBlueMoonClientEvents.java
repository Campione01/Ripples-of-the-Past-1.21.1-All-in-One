package rotp.addons.darkbluemoon.client;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.client.render.BarnaclePullRenderer;
import rotp.addons.darkbluemoon.client.render.BarnacleRenderer;
import rotp.addons.darkbluemoon.client.render.MiniTornadoRenderer;
import rotp.addons.darkbluemoon.client.render.ScaleShotRenderer;
import rotp.addons.darkbluemoon.client.render.WhirlpoolRenderer;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.ui.marker.MarkerRenderer;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** 1.16 ClientInit: the renderers, the trap marker and the necklace's look. */
@EventBusSubscriber(modid = DarkBlueMoonAddon.MOD_ID, value = Dist.CLIENT)
public final class DarkBlueMoonClientEvents {
    private DarkBlueMoonClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DarkBlueMoonAddon.DARK_BLUE_MOON_ENTITY.get(), StandEntityRenderer::new);
        event.registerEntityRenderer(DarkBlueMoonAddon.SCALE_SHOT_ENTITY.get(), ScaleShotRenderer::new);
        event.registerEntityRenderer(DarkBlueMoonAddon.BARNACLE_ENTITY.get(), BarnacleRenderer::new);
        event.registerEntityRenderer(DarkBlueMoonAddon.BARNACLE_PULL_ENTITY.get(), BarnaclePullRenderer::new);
        event.registerEntityRenderer(DarkBlueMoonAddon.WHIRLPOOL.get(), WhirlpoolRenderer::new);
        event.registerEntityRenderer(DarkBlueMoonAddon.MINI_WATER_TORNADO.get(), MiniTornadoRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DarkBlueMoonModels.SCALE_SHOT, DarkBlueMoonModels::scaleShot);
        event.registerLayerDefinition(DarkBlueMoonModels.BARNACLE, DarkBlueMoonModels::barnacle);
        event.registerLayerDefinition(DarkBlueMoonModels.BARNACLE_PULL, DarkBlueMoonModels::barnaclePull);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new BarnacleNecklaceClientExtensions(), DarkBlueMoonAddon.BARNACLE_NECKLACE.get());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MarkerRenderer.registerMarkerRenderer(new BarnacleTrapMarker(Minecraft.getInstance())));
    }
}
