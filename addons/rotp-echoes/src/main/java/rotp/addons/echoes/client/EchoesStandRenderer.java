package rotp.addons.echoes.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.echoes.stand.EchoesStandEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class EchoesStandRenderer extends StandEntityRenderer<
        EchoesStandEntity, EchoesStandRenderState, EchoesStandModel> {
    public EchoesStandRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EchoesStandModel createStandModel(LayerDefinition definition) {
        return new EchoesStandModel(definition.bakeRoot());
    }

    @Override
    public EchoesStandRenderState createRenderState() {
        return new EchoesStandRenderState();
    }

    @Override
    public void extractRenderState(
            EchoesStandEntity entity,
            EchoesStandRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.yRot = entity.getSyncedHeadYaw();
        renderState.xRot = entity.getSyncedHeadPitch();
        renderState.tailHidden = entity.isTailOut();
    }
}
