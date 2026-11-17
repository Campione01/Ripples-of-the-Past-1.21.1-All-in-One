package rotp.addons.kingcrimson.client;

import java.util.Set;

import rotp.core.api.client.render.ClientRenderCompatibility;
import rotp.core.api.client.render.ClientSkyPresentation;
import rotp.core.api.client.render.ClientSkyPresentationProviders;
import rotp.core.api.client.render.ClientSkyRenderers;
import rotp.core.api.client.render.LivingEntityMaterialTint;
import rotp.core.api.client.render.LivingEntityMaterialTintPolicies;
import rotp.core.api.client.render.ObserverWorldRenderPolicies;
import rotp.core.api.client.render.ObserverWorldRenderPolicyProvider.Pass;
import rotp.core.api.client.time.ClientEntityClockRates;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.powersystem.standpower.StandUtil;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.epitaph.EpitaphClientSession;
import rotp.addons.kingcrimson.client.epitaph.EpitaphNotice;
import rotp.addons.kingcrimson.client.epitaph.EpitaphPhantomRenderer;
import rotp.addons.kingcrimson.client.epitaph.PhantomContext;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterClient;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterIrisPresentation;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterIrisConstants;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterPackRouter;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPackRoute;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterDiagnostics;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterUniverse;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(
        modid = KingCrimsonAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class KingCrimsonClientEvents {
    // The particle pass is drawn: which particles exist in an erase is KingCrimsonEraseOutsideView's to say (the
    // skills at work outside, EV1105AC section 7).
    private static final Set<Pass> TIME_ERASE_SUPPRESSED_PASSES =
            Set.of(
                    Pass.TERRAIN,
                    Pass.WEATHER,
                    Pass.BLOCK_ENTITIES);
    // The moments before the plates are ready: the ground stays whole under the new sky.
    private static final Set<Pass> TIME_ERASE_SUPPRESSED_PASSES_TERRAIN_KEPT =
            Set.of(
                    Pass.WEATHER,
                    Pass.BLOCK_ENTITIES);
    private static final KingCrimsonTimeEraseSkyRenderer
            TIME_ERASE_SKY_RENDERER =
                    new KingCrimsonTimeEraseSkyRenderer();
    private static final ClientSkyPresentation IRIS_TIME_ERASE_NIGHT =
            new ClientSkyPresentation() {
                @Override
                public float timeOfDay(float original, float partialTick) {
                    // The real ground held until the plates are ready keeps its daylight: under the night it went dark for the
                    // first frames and the plates then came up bright, a blink at every press. Only a pack that declares the void
                    // lights the plates by their ids and wants the night; another pack lights them by its sun, which the night put
                    // out (Revoxelation's plates went black).
                    return KingCrimsonShatterDiagnostics.NIGHT_OFF || KingCrimsonShatterClient.holdsTerrain()
                            || KingCrimsonShatterPackRouter.current() != KingCrimsonShatterPackRoute.DECLARED
                            ? original : KingCrimsonShatterIrisConstants.IRIS_TIME_ERASE_NIGHT;
                }
            };
    private static boolean clientPoliciesRegistered;

    private KingCrimsonClientEvents() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(KingCrimsonShatterIrisPresentation::registerEntityCullingWhitelist);
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        registerClientPolicies();
        event.registerEntityRenderer(
                KingCrimsonAddon.KING_CRIMSON_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                KingCrimsonAddon.KC_AFTERIMAGE_ENTITY.get(),
                KingCrimsonAfterimageRenderer::new);
        event.registerEntityRenderer(
                KingCrimsonAddon.TIME_ERASE_DECOY_ENTITY.get(),
                KingCrimsonTimeEraseDecoyRenderer::new);
        event.registerEntityRenderer(KingCrimsonAddon.PHANTOM_CARRIER_ENTITY.get(), EpitaphPhantomRenderer::new);
        // Never drawn: no client ever has one.
        event.registerEntityRenderer(KingCrimsonAddon.PHANTOM_BRAIN_ENTITY.get(), NoopRenderer::new);
        event.registerEntityRenderer(KingCrimsonAddon.TIME_ERASE_DRIFT_BODY_ENTITY.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) {
                continue;
            }
            renderer.addLayer(
                    new KingCrimsonEpitaphLayer(renderer));
            renderer.addLayer(
                    KingCrimsonHalfBlindnessLayer.left(renderer));
            renderer.addLayer(
                    KingCrimsonHalfBlindnessLayer.right(renderer));
        }
    }

    @SubscribeEvent
    public static void registerGuiLayers(
            RegisterGuiLayersEvent event) {
        event.registerBelowAll(
                KingCrimsonAddon.id("time_erase_iris_sky"),
                new KingCrimsonTimeEraseIrisOverlay());
        // 1.16 drew the half blindness in RenderGameOverlayEvent.Pre(ALL): under the hotbar, hearts and chat
        event.registerBelow(
                VanillaGuiLayers.CAMERA_OVERLAYS,
                KingCrimsonAddon.id("half_blindness"),
                new KingCrimsonHalfBlindnessOverlay());
        // Where the core draws its own splashes.
        event.registerAbove(
                VanillaGuiLayers.CAMERA_OVERLAYS,
                KingCrimsonAddon.id("blood_throw_splash"),
                new KingCrimsonBloodSplashOverlay());
        event.registerAboveAll(
                KingCrimsonAddon.id("temporal"),
                new KingCrimsonTemporalOverlay());
        event.registerAboveAll(KingCrimsonAddon.id("epitaph_notice"), new EpitaphNotice());
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        KingCrimsonTimeEraseSkyRenderer.onRegisterShaders(event);
        KingCrimsonShatterUniverse.onRegisterShaders(event);
        KingCrimsonAfterimageHalo.onRegisterShaders(event);
        KingCrimsonTimeEraseTerrainOutline.onRegisterShaders(event);
    }

    private static void registerClientPolicies() {
        if (clientPoliciesRegistered) {
            return;
        }
        clientPoliciesRegistered = true;
        ObserverWorldRenderPolicies.register(
                KingCrimsonAddon.id(
                        "time_erase_world_suppression"),
                context -> {
                    if (context.observer() == null
                            || !KingCrimsonAddon
                                    .temporalState(context.observer())
                                    .isErasing(context.gameTime())) {
                        return Set.of();
                    }
                    return KingCrimsonShatterClient.holdsTerrain()
                            ? TIME_ERASE_SUPPRESSED_PASSES_TERRAIN_KEPT
                            : TIME_ERASE_SUPPRESSED_PASSES;
                });
        // The highest but for Gold Experience Requiem's cosmos: the owner's rule.
        ClientSkyRenderers.register(
                KingCrimsonAddon.id("time_erase_sky"),
                ClientSkyRenderers.HIGHEST_PRIORITY - 1,
                query -> KingCrimsonTemporalClientState.localTimeErase(
                                query.level())
                        ? TIME_ERASE_SKY_RENDERER
                        : null);
        ClientSkyPresentationProviders.register(
                KingCrimsonAddon.id("time_erase_iris_night"),
                ClientSkyPresentationProviders.HIGHEST_PRIORITY - 1,
                query -> KingCrimsonTemporalClientState.localTimeErase(query.level())
                        && ClientRenderCompatibility.snapshot().irisShaderPackInUse()
                        ? IRIS_TIME_ERASE_NIGHT
                        : null);
        LivingEntityMaterialTintPolicies.register(
                KingCrimsonAddon.id("time_erase_living_tint"),
                query -> timeEraseTint(query.entity()));
        // Epitaph, one player online: the bodies held to their tracks live a quarter as fast, and so do their animations.
        ClientEntityClockRates.register(KingCrimsonAddon.id("epitaph_bodies"), EpitaphClientSession::bodyRate);
    }

    private static LivingEntityMaterialTint timeEraseTint(
            LivingEntity entity) {
        // A phantom of Epitaph has the look of the foreseen future, in an erase too (T9).
        if (PhantomContext.active) {
            return null;
        }
        if (!KingCrimsonTemporalClientState.localTimeErase()
                || KingCrimsonAfterimageRenderer
                        .isRenderingAfterimage()) {
            return null;
        }

        LivingEntity standUser = StandUtil.getStandUser(entity);
        // His own decoy keeps its colours. The decoy of another eraser is tinted like the player it stands for:
        // nothing tells this eraser that the other erases too.
        if (standUser instanceof KingCrimsonTimeEraseDecoyEntity decoy
                && decoy.getOwner() instanceof Player owner && owner.isLocalPlayer()) {
            return null;
        }
        if (standUser instanceof Player player
                && KingCrimsonAddon.temporalState(player).isErasing(
                        player.level().getGameTime())) {
            return null;
        }
        return originalArgb -> originalArgb & 0xFF000000
                | (originalArgb >> 17 & 0x7F) << 16;
    }
}
