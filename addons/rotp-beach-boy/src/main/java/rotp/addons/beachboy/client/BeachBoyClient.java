package rotp.addons.beachboy.client;

import java.util.OptionalInt;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.ability.BeachBoyToggleAbility;
import rotp.addons.beachboy.client.render.BeachBoyHookRenderer;
import rotp.addons.beachboy.client.render.BeachBoyModels;
import rotp.addons.beachboy.client.render.BeachBoyRodRenderer;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.client.entityrender.entities.NoopEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** 1.16 ClientInit (Beach Boy's part): the renderers, the rod's model, the markers and the hook's client tick. */
@Mod(value = BeachBoyAddon.MOD_ID, dist = Dist.CLIENT)
public final class BeachBoyClient {
    public BeachBoyClient(IEventBus modEventBus) {
        modEventBus.addListener(BeachBoyClient::registerRenderers);
        modEventBus.addListener(BeachBoyClient::registerClientExtensions);
        modEventBus.addListener(BeachBoyClient::registerReloadListeners);
        modEventBus.addListener(BeachBoyClient::clientSetup);
        BeachBoyHookEntity.clientTick = hook -> {
            BeachBoyClientInput.applyIfRemote(hook);
            BeachBoyClientReel.tickSound(hook);
        };
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 1.16 BeachBoyStandRenderer drew nothing
        event.registerEntityRenderer(BeachBoyAddon.BEACH_BOY_ENTITY.get(), NoopEntityRenderer::new);
        event.registerEntityRenderer(BeachBoyAddon.BEACH_BOY_HOOK.get(), BeachBoyHookRenderer::new);
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private BeachBoyRodRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new BeachBoyRodRenderer();
                }
                return renderer;
            }
        }, BeachBoyAddon.BEACH_BOY_ROD.get());
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(BeachBoyModels.INSTANCE);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BeachBoyMarkers.register(Minecraft.getInstance());
            // 1.16 greenSelection: a switch that is on is framed green
            AbilitySelectionVisualPolicies.register(BeachBoyAddon.id("switch_on"),
                    query -> query.ability() instanceof BeachBoyToggleAbility toggle && toggle.isOn(query.power())
                            ? OptionalInt.of(0xFF00FF00) : OptionalInt.empty());
        });
    }
}
