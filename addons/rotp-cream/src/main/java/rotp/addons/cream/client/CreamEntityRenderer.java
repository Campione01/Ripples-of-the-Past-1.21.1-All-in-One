package rotp.addons.cream.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.cream.entity.CreamEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

// 1.16 CreamRenderer drew Cream like any Stand: hidden whole by full
// invisibility in the void ball and the dash, plain inside Cream.
public final class CreamEntityRenderer
        extends StandEntityRenderer<
                CreamEntity,
                CreamRenderState,
                StandEntityModel<CreamEntity, CreamRenderState>> {
    public CreamEntityRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public CreamRenderState createRenderState() {
        return new CreamRenderState();
    }

    @Override
    public void extractRenderState(
            CreamEntity entity,
            CreamRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.voidMode = entity.getVoidMode();
    }
}
