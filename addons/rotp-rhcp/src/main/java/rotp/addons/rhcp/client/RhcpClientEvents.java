package rotp.addons.rhcp.client;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.client.entityanim.barrage.BarrageSwings;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.ability.RhcpAbility;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(
        modid = RhcpAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class RhcpClientEvents {
    private RhcpClientEvents() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(RhcpAddon.MOD_ID).ifPresent(container ->
                container.registerExtensionPoint(
                        IConfigScreenFactory.class, ConfigurationScreen::new));
        event.enqueueWork(() -> BarrageSwings.BARRAGE_SWING_TYPES.put(
                RhcpBarrageSwing.TYPE, RhcpBarrageSwing::addSwings));
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                RhcpAddon.RHCP_ENTITY.get(),
                RhcpRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(
            RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                RhcpAddon.RHCP_PARTICLE.get(),
                RhcpParticle.ElectricProvider::new);
        event.registerSpriteSet(
                RhcpAddon.RHCP_FLASH.get(),
                RhcpParticle.FlashProvider::new);
    }

    // 1.16 framed a selected chase or electrocute green while usable (StandEntityActionModifier.greenSelection).
    @SubscribeEvent
    public static void registerSelectionPolicy(
            RegisterGuiLayersEvent event) {
        AbilitySelectionVisualPolicies.register(
                RhcpAddon.id("follow_up_selection"),
                RhcpAbility::followUpSelectionTint);
    }

    @SubscribeEvent
    public static void registerHud(
            RegisterGuiLayersEvent event) {
        // 1.16 drew the battery in RenderGameOverlayEvent.Pre(ALL), under the whole vanilla HUD
        event.registerBelowAll(
                RhcpAddon.id("battery"),
                new RhcpHud());
    }
}
