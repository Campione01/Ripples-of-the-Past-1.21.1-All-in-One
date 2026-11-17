package rotp.addons.highwaystar.client;

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.network.HighwayStarPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = HighwayStarAddon.MOD_ID, dist = Dist.CLIENT)
public final class HighwayStarClient {
    public HighwayStarClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(HighwayStarClient::registerRenderers);
        HighwayStarRoomClient.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderLivingEvent.Pre.class,
                event -> HighwayStarSmellVision.onRenderLiving(event));
        HighwayStarPayloads.installClientHandlers(
                HighwayStarSmellVision::onSmellPayload,
                payload -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.screen == null) {
                        mc.setScreen(new TrackingScreen(payload.targets()));
                    }
                });
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HighwayStarAddon.HIGHWAY_STAR_ENTITY.get(), HighwayStarRenderer::new);
        event.registerEntityRenderer(HighwayStarAddon.SOLE_ENTITY.get(), HighwayStarSoleRenderer::new);
        event.registerEntityRenderer(HighwayStarAddon.ROOM_ENTITY.get(), HighwayStarRoomRenderer::new);
    }
}
