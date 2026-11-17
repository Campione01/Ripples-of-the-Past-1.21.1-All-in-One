package rotp.addons.highwaystar.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.io.Reader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarRoomEntity;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel;

public final class HighwayStarRoomRenderer extends EntityRenderer<HighwayStarRoomEntity> {
    private static final ResourceLocation TEXTURE = HighwayStarAddon.id("textures/entity/highway_star_room.png");
    private final ModelPart model;

    public HighwayStarRoomRenderer(EntityRendererProvider.Context context) {
        super(context);
        try (Reader reader = context.getResourceManager()
                .getResourceOrThrow(HighwayStarAddon.id("geo/highway_star_room.geo.json")).openAsReader()) {
            model = ParseModEntityModel.parse(JsonParser.parseReader(reader),
                    ParseModEntityModel.ModelFormat.GECKO).bakeRoot();
        } catch (Exception error) {
            throw new IllegalStateException("Could not load Highway Star room door", error);
        }
    }

    @Override
    public void render(HighwayStarRoomEntity room, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        if (room.isConsumed()) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-room.getFacing().toYRot()));
        pose.translate(0,0,0.025);
        var player = Minecraft.getInstance().player;
        RenderType opening = player != null && room.distanceToSqr(player) <= HighwayStarRoomClient.PREVIEW_RANGE_SQR
                && room.getClientPreview() instanceof HighwayStarRoomPreview preview ? preview.opening() : null;
        if (opening != null) {
            HighwayStarRoomScene.doorImage(pose, buffers, opening);
        } else {
            HighwayStarRoomScene.quad(pose,buffers,HighwayStarRoomScene.WALL,
                    -0.375F,0.0625F,0,0.375F,0.0625F,0,0.375F,1.875F,0,-0.375F,1.875F,0,1,2,0xFFFFE4A2);
        }
        pose.pushPose();
        pose.scale(1,-1,-1);
        pose.translate(0,-1.5,0);
        model.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
        if (player != null && player.getUUID().equals(room.getOwnerUUID())) {
            LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),
                    -0.505,0,0.02,0.505,2.005,0.12,0.65F,0.88F,1F,0.35F);
        }
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(HighwayStarRoomEntity room) { return TEXTURE; }
}
