package rotp.addons.survivor.client;

import rotp.core.client.entityrender.entities.NoopEntityRenderer;

import rotp.addons.survivor.SurvivorAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = SurvivorAddon.MOD_ID, dist = Dist.CLIENT)
public final class SurvivorClient {
    public SurvivorClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(SurvivorClient::registerRenderers);
    }

    // 1.16 drew a humanoid Stand model with every cube cleared: Survivor has no visible body
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SurvivorAddon.SURVIVOR_ENTITY.get(), NoopEntityRenderer::new);
    }
}
