package rotp.addons.highwaystar.client;

import rotp.core.client.ClientGlobals;

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** 1.16 HighwayStarSoleRenderer: seen only by those who see Stands, no shadow, no name tag. */
public final class HighwayStarSoleRenderer extends MobRenderer<HighwayStarSoleEntity, HighwayStarSoleModel> {
    private static final ResourceLocation TEXTURE = HighwayStarAddon.id("textures/entity/stand/highway_star_sole.png");

    public HighwayStarSoleRenderer(EntityRendererProvider.Context context) {
        super(context, new HighwayStarSoleModel(), 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(HighwayStarSoleEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(HighwayStarSoleEntity entity, Frustum camera, double x, double y, double z) {
        return ClientGlobals.canSeeStands && super.shouldRender(entity, camera, x, y, z);
    }

    @Override
    protected boolean shouldShowName(HighwayStarSoleEntity entity) {
        return false;
    }
}
