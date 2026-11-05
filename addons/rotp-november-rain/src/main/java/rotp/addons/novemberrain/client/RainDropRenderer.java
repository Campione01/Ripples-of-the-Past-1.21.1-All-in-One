package rotp.addons.novemberrain.client;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;

import rotp.addons.novemberrain.NovemberRainAddon;
import rotp.addons.novemberrain.entity.RainDropEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The remake's RainDropRender (1.16 SimpleEntityRenderer: the model at the entity's position, not raised). */
public final class RainDropRenderer extends SimpleEntityRenderer<RainDropEntity, RainDropModel> {
    public RainDropRenderer(EntityRendererProvider.Context context) {
        super(context);
        initTexture(NovemberRainAddon.id("textures/entity/rain_drop.png"), false);
        initModel(new RainDropModel(context.bakeLayer(RainDropModel.LAYER_LOCATION)));
        offsetModelByEntityHeight(false);
    }
}
