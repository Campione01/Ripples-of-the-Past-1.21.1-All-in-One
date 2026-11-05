package rotp.addons.bastet.client;

import rotp.core.client.entityrender.entities.NoopEntityRenderer;

import rotp.addons.bastet.BastetAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = BastetAddon.MOD_ID, dist = Dist.CLIENT)
public final class BastetClient {
    public BastetClient(IEventBus modEventBus) {
        modEventBus.addListener(BastetClient::registerRenderers);
    }

    // 1.16 drew a humanoid Stand model with every cube cleared: Bastet has no visible body
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BastetAddon.BASTET_ENTITY.get(), NoopEntityRenderer::new);
        event.registerBlockEntityRenderer(BastetAddon.OUTLET_BLOCK_ENTITY.get(), BastetOutletRenderer::new);
    }
}
