package rotp.addons.nutkingcall.client;

import rotp.addons.nutkingcall.NutKingCallAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NutKingCallAddon.MOD_ID, value = Dist.CLIENT)
public final class NutKingCallClientEvents {
    private NutKingCallClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NutKingCallAddon.NUT_KING_CALL_ENTITY.get(), NutKingCallRenderer::new);
        event.registerEntityRenderer(NutKingCallAddon.NAIL_ENTITY.get(), NutKingCallProjectileRenderers.Nail::new);
        event.registerEntityRenderer(NutKingCallAddon.NUT_ENTITY.get(), NutKingCallProjectileRenderers.Nut::new);
        event.registerEntityRenderer(NutKingCallAddon.ATTACHED_NAIL_ENTITY.get(),
                NutKingCallProjectileRenderers.AttachedNail::new);
        event.registerBlockEntityRenderer(NutKingCallAddon.NAIL_BLOCK_ENTITY.get(), NutKingCallNailBlockRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NutKingCallNailModel.LAYER, NutKingCallNailModel::createLayer);
        event.registerLayerDefinition(NutKingCallNailModel.BLOCK_LAYER, NutKingCallNailModel::createBlockLayer);
        event.registerLayerDefinition(NutKingCallNutModel.LAYER, NutKingCallNutModel::createLayer);
    }
}
