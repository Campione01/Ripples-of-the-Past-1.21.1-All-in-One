package rotp.addons.yellowtemperance.client;

import rotp.core.client.entityrender.entities.NoopEntityRenderer;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.disguise.DisguiseClientBridge;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(
        modid = YellowTemperanceAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class YellowTemperanceClientEvents {
    private static final ModelLayerLocation SLIME_DEFAULT = new ModelLayerLocation(
            YellowTemperanceAddon.id("manga_slime"), "main");
    private static final ModelLayerLocation SLIME_SLIM = new ModelLayerLocation(
            YellowTemperanceAddon.id("manga_slime_slim"), "main");
    // just over the skin's own outer layer on the body and limbs
    private static final CubeDeformation SLIME_INFLATE = new CubeDeformation(0.3F);

    static {
        DisguiseClientBridge.install(
                DisguiseSelectionScreen::open);
    }

    private YellowTemperanceClientEvents() {}

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SLIME_DEFAULT,
                () -> LayerDefinition.create(PlayerModel.createMesh(SLIME_INFLATE, false), 64, 64));
        event.registerLayerDefinition(SLIME_SLIM,
                () -> LayerDefinition.create(PlayerModel.createMesh(SLIME_INFLATE, true), 64, 64));
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                YellowTemperanceAddon.CONTROLLER.get(),
                NoopEntityRenderer::new);
    }

    // 1.16 ClientInit added the erosion layer to every living renderer and the disguise layer to the
    // player's. Its YellowTemperanceLayer, the coating, was never added to anything; the slime layer here is the
    // manga's, by choice in the module's settings.
    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (var entityType : event.getEntityTypes()) {
            addErosionLayer(event.getRenderer(entityType));
        }
        EntityModelSet models = event.getEntityModels();
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) {
                continue;
            }
            addErosionLayer(renderer);
            renderer.addLayer(new YellowTemperanceDisguiseLayer(
                    renderer,
                    models.bakeLayer(ModelLayers.PLAYER),
                    models.bakeLayer(ModelLayers.PLAYER_SLIM)));
            boolean slim = skin == PlayerSkin.Model.SLIM;
            renderer.addLayer(new YellowTemperanceSlimeLayer<>(
                    renderer,
                    new PlayerModel<>(models.bakeLayer(slim ? SLIME_SLIM : SLIME_DEFAULT), slim)));
        }
    }

    @SubscribeEvent
    public static void registerHud(
            RegisterGuiLayersEvent event) {
        event.wrapLayer(
                VanillaGuiLayers.FOOD_LEVEL,
                YellowTemperanceFoodHud::new);
        event.registerAboveAll(
                YellowTemperanceAddon.id("doll_hud"),
                new YellowTemperanceDollHud());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addErosionLayer(EntityRenderer<?> renderer) {
        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(
                    new YellowTemperanceErosionLayer<>(livingRenderer));
        }
    }
}
