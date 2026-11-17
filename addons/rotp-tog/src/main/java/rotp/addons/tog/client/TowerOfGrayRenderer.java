package rotp.addons.tog.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import rotp.addons.tog.TowerOfGrayAddon;
import rotp.addons.tog.TowerOfGrayEntity;
import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.compat.v1_21_4.missingmethods.Model_1_21_2plus;

@EventBusSubscriber(modid = TowerOfGrayAddon.MOD_ID, value = Dist.CLIENT)
public final class TowerOfGrayRenderer extends
        StandEntityRenderer<TowerOfGrayEntity, StandEntityRenderState, TowerOfGrayRenderer.Model> {

    private TowerOfGrayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TowerOfGrayAddon.ENTITY.get(), TowerOfGrayRenderer::new);
    }

    @Override
    public Model createStandModel(LayerDefinition definition) {
        return new Model(definition.bakeRoot());
    }

    static class Model extends StandEntityModel<TowerOfGrayEntity, StandEntityRenderState> {
        private final ModelPart tongue;

        Model(ModelPart root) {
            super(root);
            tongue = ((Model_1_21_2plus) this).jojo_ripples$getAnyDescendantWithName("tongue").orElse(null);
        }

        @Override
        public void setupAnim(StandEntityRenderState state) {
            super.setupAnim(state);
            // The donor never sets HAS_TONGUE; its legacy pose reset also overwrote the preparatory wing flap.
            if (tongue != null) {
                tongue.visible = false;
            }
        }
    }
}
