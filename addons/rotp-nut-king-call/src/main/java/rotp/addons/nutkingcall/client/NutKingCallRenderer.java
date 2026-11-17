package rotp.addons.nutkingcall.client;

import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.nutkingcall.entity.NutKingCallEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class NutKingCallRenderer extends StandEntityRenderer<
        NutKingCallEntity, StandEntityRenderState, NutKingCallModel> {
    public NutKingCallRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public NutKingCallModel createStandModel(LayerDefinition definition) {
        return new NutKingCallModel(definition.bakeRoot());
    }
}
