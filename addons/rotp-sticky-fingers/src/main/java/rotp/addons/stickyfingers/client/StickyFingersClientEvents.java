package rotp.addons.stickyfingers.client;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.ability.StickyFingersUnzipPartAbility;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(
        modid = StickyFingersAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class StickyFingersClientEvents {
    private StickyFingersClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                StickyFingersAddon.STICKY_FINGERS_ENTITY.get(),
                StickyFingersStandRenderer::new);
        event.registerEntityRenderer(
                StickyFingersAddon
                        .STICKY_FINGERS_EXTENDED_PUNCH_ENTITY
                        .get(),
                StickyFingersExtendedPunchRenderer::new);
        event.registerEntityRenderer(
                StickyFingersAddon.PLAYER_HEAD.get(),
                DetachedBodyPartRenderer::new);
        event.registerEntityRenderer(
                StickyFingersAddon.PLAYER_ARM.get(),
                DetachedBodyPartRenderer::new);
        event.registerEntityRenderer(
                StickyFingersAddon.PLAYER_LEG.get(),
                DetachedBodyPartRenderer::new);
        event.registerBlockEntityRenderer(
                StickyFingersAddon
                        .STICKY_FINGERS_ZIPPER_BLOCK_ENTITY
                        .get(),
                StickyFingersZipperRenderer::new);
    }

    @SubscribeEvent
    public static void wrapZipperModels(
            ModelEvent.ModifyBakingResult event) {
        for (BlockState state : StickyFingersAddon.STICKY_FINGERS_ZIPPER
                .get().getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location =
                    BlockModelShaper.stateToModelLocation(state);
            BakedModel model = event.getModels().get(location);
            if (model != null
                    && !(model instanceof StickyFingersZipperBlockModel)) {
                event.getModels().put(
                        location,
                        new StickyFingersZipperBlockModel(model));
            }
        }
    }

    @SubscribeEvent
    public static void registerBlockColors(
            RegisterColorHandlersEvent.Block event) {
        event.register(
                StickyFingersZipperBlockModel::tint,
                StickyFingersAddon.STICKY_FINGERS_ZIPPER.get());
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        StickyFingersZipperVoidRenderType.registerShader(event);
    }

    /** The 1.16 carried-body-part hotbar slot (see {@link StickyFingersCarriedPartHud}). */
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(
                VanillaGuiLayers.HOTBAR,
                StickyFingersAddon.id("carried_body_part_frame"),
                StickyFingersCarriedPartHud.frame());
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                StickyFingersAddon.id("carried_body_part"),
                StickyFingersCarriedPartHud.model());
    }

    // 1.16 framed a selected unzip follow-up green while usable (StandEntityActionModifier.greenSelection).
    @SubscribeEvent
    public static void registerSelectionPolicy(
            RegisterGuiLayersEvent event) {
        AbilitySelectionVisualPolicies.register(
                StickyFingersAddon.id("unzip_follow_up_selection"),
                StickyFingersUnzipPartAbility::selectionTint);
    }

    @SubscribeEvent
    public static void registerMenuScreens(
            RegisterMenuScreensEvent event) {
        event.register(
                StickyFingersAddon
                        .STICKY_FINGERS_ENTITY_CONTAINER.get(),
                EntityZipperStorageScreen::new);
        event.register(
                StickyFingersAddon
                        .STICKY_FINGERS_BLOCK_CONTAINER.get(),
                BlockZipperStorageScreen::new);
    }
}
