package rotp.addons.nextalbum.client;

import rotp.addons.nextalbum.NextAlbumAddon;
import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.client.compat.AjaNecklaceCuriosClient;
import rotp.addons.nextalbum.client.render.CigaretteMouthLayer;
import rotp.addons.nextalbum.compat.AjaNecklaceCurios;
import rotp.addons.nextalbum.item.SpiritCigaretteItem;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import rotp.addons.nextalbum.client.particle.ResolveAuraParticle;
import rotp.addons.nextalbum.client.particle.SoundWordParticle;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = NextAlbumIds.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class NextAlbumClientSetup {
    private NextAlbumClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (AjaNecklaceCurios.isLoaded()) {
            event.enqueueWork(AjaNecklaceCuriosClient::register);
        }
        event.enqueueWork(CharacterSuitClientEvents::registerShadowSkin);
        event.enqueueWork(ResolveDownedClient::registerStandFade);
        event.enqueueWork(() -> ItemProperties.register(
                NextAlbumAddon.SPIRIT_CIGARETTE.get(),
                NextAlbumIds.id("lit"),
                (stack, level, entity, seed) ->
                        SpiritCigaretteItem.isLit(stack) ? 1.0F : 0.0F));
    }

    public static void registerConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterParticles(
            RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(NextAlbumAddon.STAND_RESOLVE_AURA.get(),
                ResolveAuraParticle.Provider::new);
        event.registerSpriteSet(NextAlbumAddon.SOUND_GO_ORANGE.get(),
                SoundWordParticle.Provider::new);
        event.registerSpriteSet(NextAlbumAddon.SOUND_DO_ORANGE.get(),
                SoundWordParticle.Provider::new);
        event.registerSpriteSet(NextAlbumAddon.SOUND_BA_ORANGE.get(),
                SoundWordParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new CigaretteMouthLayer(renderer));
            }
        }
    }
}
